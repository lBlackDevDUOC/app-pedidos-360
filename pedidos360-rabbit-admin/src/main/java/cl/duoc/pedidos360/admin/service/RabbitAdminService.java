package cl.duoc.pedidos360.admin.service;

import cl.duoc.pedidos360.admin.config.AdminProperties;
import cl.duoc.pedidos360.admin.dto.BindingResponse;
import cl.duoc.pedidos360.admin.dto.ColaResponse;
import cl.duoc.pedidos360.admin.dto.CrearBindingRequest;
import cl.duoc.pedidos360.admin.dto.CrearColaRequest;
import cl.duoc.pedidos360.admin.dto.CrearExchangeRequest;
import cl.duoc.pedidos360.admin.dto.ExchangeResponse;
import cl.duoc.pedidos360.admin.dto.PurgaResponse;
import cl.duoc.pedidos360.admin.rabbit.RabbitManagementClient;
import cl.duoc.pedidos360.admin.rabbit.RabbitManagementClient.ColaInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Exchange;
import org.springframework.amqp.core.ExchangeBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Toda la administración de RabbitMQ pasa por aquí. Los controladores llaman a métodos de alto nivel
 * (crearCola, eliminarExchange...) y no conocen AmqpAdmin, la API de Management ni los x-arguments.
 *
 * <ul>
 *   <li>Escrituras (crear/eliminar/purgar): AMQP con {@link AmqpAdmin}.</li>
 *   <li>Lecturas (listar, métricas, existencia de exchanges): API HTTP de Management.</li>
 * </ul>
 * Antes de cada operación se valida que tenga sentido (existe / no existe / no está protegido),
 * para responder errores claros en lugar de un error de canal AMQP.
 */
@Service
public class RabbitAdminService {

    private static final Logger log = LoggerFactory.getLogger(RabbitAdminService.class);

    private final AmqpAdmin amqpAdmin;
    private final RabbitManagementClient management;
    private final AdminProperties props;

    public RabbitAdminService(AmqpAdmin amqpAdmin, RabbitManagementClient management, AdminProperties props) {
        this.amqpAdmin = amqpAdmin;
        this.management = management;
        this.props = props;
    }

    // ------------------------------------------------------------------ Colas

    public List<ColaResponse> listarColas() {
        return management.listarColas().stream()
                .map(this::aRespuesta)
                .sorted(Comparator.comparing(ColaResponse::nombre))
                .toList();
    }

    public ColaResponse obtenerCola(String nombre) {
        return management.buscarCola(nombre)
                .map(this::aRespuesta)
                .orElseThrow(() -> new RecursoNoEncontradoException("La cola '" + nombre + "' no existe"));
    }

    public ColaResponse crearCola(CrearColaRequest req) {
        verificarNoProtegido(req.nombre(), "crear");
        if (amqpAdmin.getQueueInfo(req.nombre()) != null) {
            throw new RecursoYaExisteException("La cola '" + req.nombre() + "' ya existe");
        }
        if (req.deadLetterExchange() != null && !management.existeExchange(req.deadLetterExchange())) {
            throw new ConfiguracionInvalidaException(
                    "El exchange DLX '" + req.deadLetterExchange() + "' no existe: los mensajes muertos se perderían");
        }

        QueueBuilder builder = req.esDurable() ? QueueBuilder.durable(req.nombre()) : QueueBuilder.nonDurable(req.nombre());
        if (req.ttlMs() != null) {
            builder.ttl(req.ttlMs());
        }
        if (req.maxMensajes() != null) {
            builder.maxLength(req.maxMensajes());
        }
        if (req.deadLetterExchange() != null) {
            builder.deadLetterExchange(req.deadLetterExchange());
        }
        if (req.deadLetterRoutingKey() != null) {
            builder.deadLetterRoutingKey(req.deadLetterRoutingKey());
        }
        Queue cola = builder.build();
        amqpAdmin.declareQueue(cola);
        log.info("[ADMIN] Cola creada: {} argumentos={}", cola.getName(), cola.getArguments());

        return new ColaResponse(cola.getName(), cola.isDurable(), 0, 0, 0, 0, esDlq(cola.getName()), cola.getArguments());
    }

    public void eliminarCola(String nombre) {
        verificarNoProtegido(nombre, "eliminar");
        verificarColaExiste(nombre);
        amqpAdmin.deleteQueue(nombre);
        log.info("[ADMIN] Cola eliminada: {}", nombre);
    }

    /** Vacía la cola (permitido también en colas del sistema, por ejemplo para limpiar una DLQ ya revisada). */
    public PurgaResponse purgarCola(String nombre) {
        verificarColaExiste(nombre);
        int eliminados = amqpAdmin.purgeQueue(nombre);
        log.info("[ADMIN] Cola purgada: {} ({} mensajes eliminados)", nombre, eliminados);
        return new PurgaResponse(nombre, eliminados);
    }

    // ------------------------------------------------------------------ Exchanges

    /** Exchanges definidos por usuarios (se omiten el exchange por defecto y los amq.* del broker). */
    public List<ExchangeResponse> listarExchanges() {
        return management.listarExchanges().stream()
                .filter(e -> !e.name().isEmpty() && !e.name().startsWith("amq."))
                .map(e -> new ExchangeResponse(e.name(), e.type(), e.durable()))
                .sorted(Comparator.comparing(ExchangeResponse::nombre))
                .toList();
    }

    public ExchangeResponse crearExchange(CrearExchangeRequest req) {
        verificarNoProtegido(req.nombre(), "crear");
        if (management.existeExchange(req.nombre())) {
            throw new RecursoYaExisteException("El exchange '" + req.nombre() + "' ya existe");
        }
        Exchange exchange = new ExchangeBuilder(req.nombre(), req.tipo().valorAmqp()).durable(req.esDurable()).build();
        amqpAdmin.declareExchange(exchange);
        log.info("[ADMIN] Exchange creado: {} ({})", exchange.getName(), exchange.getType());
        return new ExchangeResponse(exchange.getName(), exchange.getType(), exchange.isDurable());
    }

    public void eliminarExchange(String nombre) {
        verificarNoProtegido(nombre, "eliminar");
        if (!management.existeExchange(nombre)) {
            throw new RecursoNoEncontradoException("El exchange '" + nombre + "' no existe");
        }
        amqpAdmin.deleteExchange(nombre);
        log.info("[ADMIN] Exchange eliminado: {}", nombre);
    }

    // ------------------------------------------------------------------ Bindings

    /** Bindings entre exchanges y colas (se omiten los implícitos del exchange por defecto). */
    public List<BindingResponse> listarBindings() {
        return management.listarBindings().stream()
                .filter(b -> !b.source().isEmpty() && "queue".equals(b.destinationType()))
                .map(b -> new BindingResponse(b.source(), b.destination(), b.routingKey()))
                .toList();
    }

    public BindingResponse crearBinding(CrearBindingRequest req) {
        verificarColaExiste(req.cola());
        if (!management.existeExchange(req.exchange())) {
            throw new RecursoNoEncontradoException("El exchange '" + req.exchange() + "' no existe");
        }
        String routingKey = req.routingKeyONula();
        if (existeBinding(req.exchange(), req.cola(), routingKey)) {
            throw new RecursoYaExisteException("El binding " + req.exchange() + " → " + req.cola()
                    + " (" + routingKey + ") ya existe");
        }
        amqpAdmin.declareBinding(binding(req.exchange(), req.cola(), routingKey));
        log.info("[ADMIN] Binding creado: {} → {} ({})", req.exchange(), req.cola(), routingKey);
        return new BindingResponse(req.exchange(), req.cola(), routingKey);
    }

    public void eliminarBinding(String exchange, String cola, String routingKey) {
        verificarNoProtegido(cola, "desenlazar");
        if (!existeBinding(exchange, cola, routingKey)) {
            throw new RecursoNoEncontradoException(
                    "No existe el binding " + exchange + " → " + cola + " (" + routingKey + ")");
        }
        amqpAdmin.removeBinding(binding(exchange, cola, routingKey));
        log.info("[ADMIN] Binding eliminado: {} → {} ({})", exchange, cola, routingKey);
    }

    // ------------------------------------------------------------------ Apoyo

    private void verificarNoProtegido(String nombre, String accion) {
        if (props.esProtegido(nombre)) {
            throw new RecursoProtegidoException("No se puede " + accion + " '" + nombre
                    + "': pertenece a la topología de Pedidos360 (prefijos protegidos " + props.prefijosProtegidos() + ")");
        }
    }

    private void verificarColaExiste(String nombre) {
        if (amqpAdmin.getQueueInfo(nombre) == null) {
            throw new RecursoNoEncontradoException("La cola '" + nombre + "' no existe");
        }
    }

    private boolean existeBinding(String exchange, String cola, String routingKey) {
        return management.listarBindings().stream().anyMatch(b ->
                b.source().equals(exchange) && b.destination().equals(cola)
                        && "queue".equals(b.destinationType()) && b.routingKey().equals(routingKey));
    }

    private static Binding binding(String exchange, String cola, String routingKey) {
        return new Binding(cola, Binding.DestinationType.QUEUE, exchange, routingKey, null);
    }

    private boolean esDlq(String nombre) {
        return nombre.endsWith(props.alertas().sufijoDlq());
    }

    private ColaResponse aRespuesta(ColaInfo c) {
        return new ColaResponse(
                c.name(),
                c.durable(),
                valor(c.messages()),
                valor(c.messagesReady()),
                valor(c.messagesUnacknowledged()),
                c.consumers() == null ? 0 : c.consumers(),
                esDlq(c.name()),
                c.arguments() == null ? Map.of() : c.arguments());
    }

    private static long valor(Long numero) {
        return numero == null ? 0 : numero;
    }
}
