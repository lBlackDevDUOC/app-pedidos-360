package cl.duoc.pedidos360.messaging.producer;

import cl.duoc.pedidos360.messaging.config.KafkaTopicosProperties;
import cl.duoc.pedidos360.messaging.event.EventoAuditoria;
import cl.duoc.pedidos360.messaging.event.EventoPedido;
import cl.duoc.pedidos360.model.Actor;
import cl.duoc.pedidos360.model.EstadoPedido;
import cl.duoc.pedidos360.model.Pedido;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.UUID;

/**
 * Productor Kafka de ms-pedidos. Por cada hecho de negocio publica dos mensajes:
 * <ul>
 *   <li>{@code orders.events} con key = id del pedido → orden garantizado por pedido (misma partición).</li>
 *   <li>{@code audit.timeline} con key = id del evento → quién/qué/cuándo/desde dónde.</li>
 * </ul>
 * Los valores se serializan a JSON con el JsonSerializer configurado en application.yml.
 */
@Component
public class KafkaEventoNegocioPublisher implements EventoNegocioPublisher {

    private static final Logger log = LoggerFactory.getLogger(KafkaEventoNegocioPublisher.class);
    private static final String ORIGEN = "ms-pedidos";

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final KafkaTopicosProperties props;

    public KafkaEventoNegocioPublisher(KafkaTemplate<String, Object> kafkaTemplate, KafkaTopicosProperties props) {
        this.kafkaTemplate = kafkaTemplate;
        this.props = props;
    }

    @Override
    public void pedidoCreado(Pedido pedido, Actor actor) {
        publicar(pedido, null, actor, "PEDIDO_CREADO", "Pedido creado por $" + pedido.getTotal());
    }

    @Override
    public void estadoCambiado(Pedido pedido, EstadoPedido anterior, Actor actor) {
        publicar(pedido, anterior, actor, "PEDIDO_ESTADO_CAMBIADO", anterior + " → " + pedido.getEstado());
    }

    /** Nombre del evento según el estado al que llegó el pedido (convención del caso Pedidos360). */
    static String tipoDeEvento(EstadoPedido estado) {
        return switch (estado) {
            case CREADO -> "OrderCreated";
            case ACEPTADO -> "OrderAccepted";
            case EN_PREPARACION -> "OrderPreparing";
            case DESPACHADO -> "OrderDispatched";
            case ENTREGADO -> "OrderDelivered";
            case CANCELADO -> "OrderCancelled";
        };
    }

    private void publicar(Pedido pedido, EstadoPedido anterior, Actor actor, String accion, String detalle) {
        Instant ahora = Instant.now();
        String traceId = UUID.randomUUID().toString();
        String correlationId = "pedido-" + pedido.getId();

        EventoPedido evento = new EventoPedido(
                UUID.randomUUID().toString(),
                tipoDeEvento(pedido.getEstado()),
                EventoPedido.VERSION_ACTUAL,
                ahora,
                ORIGEN,
                traceId,
                correlationId,
                pedido.getId(),
                pedido.getCliente().getOid(),
                pedido.getCliente().getNombre(),
                pedido.getTotal(),
                anterior == null ? null : anterior.name(),
                pedido.getEstado().name(),
                pedido.getFechaCreacion(),
                pedido.getFechaEntrega(),
                actor.id());

        EventoAuditoria auditoria = new EventoAuditoria(
                UUID.randomUUID().toString(),
                EventoAuditoria.VERSION_ACTUAL,
                ahora,
                ORIGEN,
                traceId,
                correlationId,
                accion,
                "PEDIDO",
                String.valueOf(pedido.getId()),
                detalle,
                actor.id(),
                actor.nombre(),
                actor.roles(),
                actor.ip(),
                actor.userAgent());

        // Se envía recién cuando la transacción de la BD hizo commit: nunca se anuncia un cambio que después
        // se revirtió (rollback). Si no hay transacción activa, el dato ya está guardado y se envía de inmediato.
        despuesDelCommit(() -> {
            enviar(props.topicos().pedidos(), String.valueOf(pedido.getId()), evento, evento.tipo(), evento.eventoId());
            enviar(props.topicos().auditoria(), auditoria.eventoId(), auditoria, accion, auditoria.eventoId());
        });
    }

    private void enviar(String topico, String key, Object valor, String tipo, String eventoId) {
        try {
            // send() es asíncrono: el request HTTP no espera la confirmación del broker
            kafkaTemplate.send(topico, key, valor).whenComplete((resultado, error) -> {
                if (error == null) {
                    var meta = resultado.getRecordMetadata();
                    log.info("[KAFKA-PUBLICADO] {} {} key={} → partición {} offset {}",
                            topico, tipo, key, meta.partition(), meta.offset());
                } else {
                    log.error("[KAFKA-FALLIDO] {} {} key={} eventoId={}: {}",
                            topico, tipo, key, eventoId, error.getMessage());
                }
            });
        } catch (RuntimeException e) {
            // Broker inalcanzable (max.block.ms): el negocio sigue funcionando, el fallo queda registrado
            log.error("[KAFKA-FALLIDO] {} {} key={} eventoId={}: {}", topico, tipo, key, eventoId, e.getMessage());
        }
    }

    private static void despuesDelCommit(Runnable accion) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    accion.run();
                }
            });
        } else {
            accion.run();
        }
    }
}
