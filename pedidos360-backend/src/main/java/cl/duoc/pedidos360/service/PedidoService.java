package cl.duoc.pedidos360.service;

import cl.duoc.pedidos360.messaging.producer.EventoNegocioPublisher;
import cl.duoc.pedidos360.messaging.producer.PedidoEventPublisher;
import cl.duoc.pedidos360.model.Actor;
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
 * Lógica de negocio de pedidos. No conoce RabbitMQ ni Kafka: solo habla con dos interfaces.
 * <ul>
 *   <li>{@link PedidoEventPublisher} (RabbitMQ): comando de trabajo "generar comprobante y notificar".</li>
 *   <li>{@link EventoNegocioPublisher} (Kafka): hechos de negocio para reportería y auditoría en tiempo real.</li>
 * </ul>
 */
@Service
public class PedidoService {

    private static final Logger log = LoggerFactory.getLogger(PedidoService.class);

    private final PedidoRepository pedidoRepository;
    private final PedidoEventPublisher eventPublisher;
    private final EventoNegocioPublisher eventoNegocioPublisher;

    public PedidoService(PedidoRepository pedidoRepository, PedidoEventPublisher eventPublisher,
                         EventoNegocioPublisher eventoNegocioPublisher) {
        this.pedidoRepository = pedidoRepository;
        this.eventPublisher = eventPublisher;
        this.eventoNegocioPublisher = eventoNegocioPublisher;
    }

    /**
     * Pedidos con filtros opcionales por estado y rango de fecha de creación.
     * Operador y Admin ven los de todos los clientes; un Cliente solo los suyos.
     */
    public List<Pedido> listar(Cliente cliente, Actor actor, EstadoPedido estado, LocalDateTime desde,
                               LocalDateTime hasta) {
        String soloDe = actor.gestionaPedidos() ? null : cliente.getOid();
        Specification<Pedido> filtro = Specification.allOf(
                delCliente(soloDe), conEstado(estado), creadoDesde(desde), creadoHasta(hasta));
        return pedidoRepository.findAll(filtro, Sort.by(Sort.Direction.DESC, "fechaCreacion"));
    }

    /**
     * Pedido por id. Un Cliente solo ve los suyos: si es de otro responde 404 (no 403), así no se revela
     * que ese id existe.
     */
    public Pedido obtener(Long id, Cliente cliente, Actor actor) {
        return pedidoRepository.findById(id)
                .filter(p -> actor.gestionaPedidos() || p.getCliente().getId().equals(cliente.getId()))
                .orElseThrow(() -> new PedidoNoEncontradoException(id));
    }

    /**
     * Guarda el pedido (siempre nace en CREADO) y luego publica el evento. El comprobante y la notificación
     * se generan en segundo plano (otros microservicios), así la respuesta al usuario no espera por ellos.
     */
    public Pedido crear(Cliente cliente, Double total, Actor actor) {
        Pedido guardado = pedidoRepository.save(new Pedido(cliente, total));
        eventPublisher.publicarPedidoCreado(guardado);
        eventoNegocioPublisher.pedidoCreado(guardado, actor);
        return guardado;
    }

    /**
     * Avanza el pedido en su ciclo de vida. La validación de la transición la hace la entidad
     * ({@link Pedido#cambiarEstado}); si no es válida se lanza TransicionInvalidaException (409).
     * El evento a Kafka se publica recién después del commit (ver KafkaEventoNegocioPublisher).
     * Permisos: Operador y Admin mueven cualquier pedido; un Cliente solo puede CANCELAR uno suyo.
     */
    @Transactional
    public Pedido cambiarEstado(Long id, EstadoPedido destino, Cliente cliente, Actor actor) {
        Pedido pedido = obtener(id, cliente, actor);
        if (!actor.gestionaPedidos() && destino != EstadoPedido.CANCELADO) {
            throw new OperacionNoPermitidaException(
                    "Un cliente solo puede cancelar sus pedidos; el resto de los estados los gestiona un Operador");
        }
        EstadoPedido anterior = pedido.cambiarEstado(destino);
        Pedido guardado = pedidoRepository.save(pedido);
        eventoNegocioPublisher.estadoCambiado(guardado, anterior, actor);
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
