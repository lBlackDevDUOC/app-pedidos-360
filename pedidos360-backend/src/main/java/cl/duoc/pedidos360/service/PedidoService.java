package cl.duoc.pedidos360.service;

import cl.duoc.pedidos360.messaging.producer.EventoNegocioPublisher;
import cl.duoc.pedidos360.messaging.producer.PedidoEventPublisher;
import cl.duoc.pedidos360.model.Actor;
import cl.duoc.pedidos360.model.Cliente;
import cl.duoc.pedidos360.model.EstadoPedido;
import cl.duoc.pedidos360.model.ItemPedido;
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
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

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
    private final Catalogo catalogo;

    /** Estados en los que el stock del pedido ya está descontado: si se cancela desde aquí, hay que reponerlo. */
    private static final Set<EstadoPedido> CON_STOCK_DESCONTADO = Set.of(EstadoPedido.ACEPTADO, EstadoPedido.EN_PREPARACION);

    public record LineaPedido(Long productoId, int cantidad) {}

    public PedidoService(PedidoRepository pedidoRepository, PedidoEventPublisher eventPublisher,
                         EventoNegocioPublisher eventoNegocioPublisher, Catalogo catalogo) {
        this.pedidoRepository = pedidoRepository;
        this.eventPublisher = eventPublisher;
        this.eventoNegocioPublisher = eventoNegocioPublisher;
        this.catalogo = catalogo;
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
        return guardarYPublicar(new Pedido(cliente, total), actor);
    }

    /**
     * Pedido con productos del catálogo. Los precios y nombres se toman de ms-catalog (nunca del navegador:
     * el cliente no puede inventar el precio). El stock NO se descuenta aquí, sino cuando un Operador lo acepta.
     */
    public Pedido crear(Cliente cliente, List<LineaPedido> lineas, Actor actor) {
        Map<Long, Catalogo.ProductoCatalogo> productos = catalogo.productos(
                lineas.stream().map(LineaPedido::productoId).toList()).stream()
                .collect(Collectors.toMap(Catalogo.ProductoCatalogo::id, Function.identity()));
        List<ItemPedido> items = lineas.stream().map(l -> {
            Catalogo.ProductoCatalogo p = productos.get(l.productoId());
            if (p == null || !p.activo()) {
                throw new Catalogo.ProductoNoDisponibleException("Producto no disponible: " + l.productoId());
            }
            return new ItemPedido(p.id(), p.nombre(), l.cantidad(), p.precio());
        }).toList();
        return guardarYPublicar(new Pedido(cliente, items), actor);
    }

    private Pedido guardarYPublicar(Pedido nuevo, Actor actor) {
        Pedido guardado = pedidoRepository.save(nuevo);
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
        if (pedido.tieneItems() && destino == EstadoPedido.ACEPTADO) {
            // Regla del caso: el stock baja al aceptar. Si no alcanza, lanza excepción y el pedido sigue CREADO
            catalogo.descontarStock(pedido.getId(), pedido.getItems());
        } else if (pedido.tieneItems() && destino == EstadoPedido.CANCELADO && CON_STOCK_DESCONTADO.contains(anterior)) {
            catalogo.reponerStock(pedido.getId());
        }
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
