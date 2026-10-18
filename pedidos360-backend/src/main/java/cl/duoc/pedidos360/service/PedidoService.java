package cl.duoc.pedidos360.service;

import cl.duoc.pedidos360.messaging.producer.PedidoEventPublisher;
import cl.duoc.pedidos360.model.Cliente;
import cl.duoc.pedidos360.model.Pedido;
import cl.duoc.pedidos360.repository.PedidoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Lógica de negocio de pedidos. No conoce RabbitMQ: anuncia los pedidos nuevos a través de
 * {@link PedidoEventPublisher} y recibe el comprobante ya traducido a parámetros simples.
 */
@Service
public class PedidoService {

    private static final Logger log = LoggerFactory.getLogger(PedidoService.class);

    private final PedidoRepository pedidoRepository;
    private final PedidoEventPublisher eventPublisher;

    public PedidoService(PedidoRepository pedidoRepository, PedidoEventPublisher eventPublisher) {
        this.pedidoRepository = pedidoRepository;
        this.eventPublisher = eventPublisher;
    }

    public List<Pedido> listar(Cliente cliente) {
        return pedidoRepository.findByClienteOidOrderByFechaCreacionDesc(cliente.getOid());
    }

    /** Pedido propio por id; si no existe o es de otro cliente lanza {@link PedidoNoEncontradoException}. */
    public Pedido obtener(Long id, Cliente cliente) {
        return pedidoRepository.findById(id)
                .filter(p -> p.getCliente().getId().equals(cliente.getId()))
                .orElseThrow(() -> new PedidoNoEncontradoException(id));
    }

    /**
     * Guarda el pedido y luego publica el evento. El comprobante y la notificación se generan
     * en segundo plano (otros microservicios), así la respuesta al usuario no espera por ellos.
     */
    public Pedido crear(Cliente cliente, Double total, String estado) {
        Pedido guardado = pedidoRepository.save(new Pedido(cliente, total, estado));
        eventPublisher.publicarPedidoCreado(guardado);
        return guardado;
    }

    /**
     * Asocia el comprobante emitido al pedido. Es idempotente: si el mensaje llega duplicado
     * (RabbitMQ garantiza entrega "al menos una vez"), la segunda vez no cambia nada.
     */
    @Transactional
    public void registrarComprobante(Long pedidoId, String numeroComprobante, LocalDateTime fechaEmision) {
        Pedido pedido = pedidoRepository.findById(pedidoId)
                .orElseThrow(() -> new PedidoNoEncontradoException(pedidoId));

        if (numeroComprobante.equals(pedido.getNumeroComprobante())) {
            log.info("Pedido {} ya tenía el comprobante {}, mensaje duplicado ignorado", pedidoId, numeroComprobante);
            return;
        }
        pedido.setNumeroComprobante(numeroComprobante);
        pedido.setFechaComprobante(fechaEmision);
        pedidoRepository.save(pedido);
        log.info("Pedido {} actualizado con comprobante {}", pedidoId, numeroComprobante);
    }
}
