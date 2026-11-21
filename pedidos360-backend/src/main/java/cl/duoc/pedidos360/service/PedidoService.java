package cl.duoc.pedidos360.service;

import cl.duoc.pedidos360.messaging.producer.PedidoEventPublisher;
import cl.duoc.pedidos360.model.Cliente;
import cl.duoc.pedidos360.model.EstadoPedido;
import cl.duoc.pedidos360.model.Pedido;
import cl.duoc.pedidos360.repository.PedidoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

import static cl.duoc.pedidos360.repository.PedidoSpecs.conEstado;
import static cl.duoc.pedidos360.repository.PedidoSpecs.creadoDesde;
import static cl.duoc.pedidos360.repository.PedidoSpecs.creadoHasta;
import static cl.duoc.pedidos360.repository.PedidoSpecs.delCliente;

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

    /** Pedidos del cliente con filtros opcionales por estado y rango de fecha de creación. */
    public List<Pedido> listar(Cliente cliente, EstadoPedido estado, LocalDateTime desde, LocalDateTime hasta) {
        Specification<Pedido> filtro = Specification.allOf(
                delCliente(cliente.getOid()), conEstado(estado), creadoDesde(desde), creadoHasta(hasta));
        return pedidoRepository.findAll(filtro, Sort.by(Sort.Direction.DESC, "fechaCreacion"));
    }

    /** Pedido propio por id; si no existe o es de otro cliente lanza {@link PedidoNoEncontradoException}. */
    public Pedido obtener(Long id, Cliente cliente) {
        return pedidoRepository.findById(id)
                .filter(p -> p.getCliente().getId().equals(cliente.getId()))
                .orElseThrow(() -> new PedidoNoEncontradoException(id));
    }

    /**
     * Guarda el pedido (siempre nace en CREADO) y luego publica el evento. El comprobante y la notificación
     * se generan en segundo plano (otros microservicios), así la respuesta al usuario no espera por ellos.
     */
    public Pedido crear(Cliente cliente, Double total) {
        Pedido guardado = pedidoRepository.save(new Pedido(cliente, total));
        eventPublisher.publicarPedidoCreado(guardado);
        return guardado;
    }

    /**
     * Avanza el pedido en su ciclo de vida. La validación de la transición la hace la entidad
     * ({@link Pedido#cambiarEstado}); si no es válida se lanza TransicionInvalidaException (409).
     */
    @Transactional
    public Pedido cambiarEstado(Long id, EstadoPedido destino, Cliente cliente) {
        Pedido pedido = obtener(id, cliente);
        EstadoPedido anterior = pedido.cambiarEstado(destino);
        Pedido guardado = pedidoRepository.save(pedido);
        log.info("Pedido {} cambió de {} a {}", id, anterior, destino);
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
