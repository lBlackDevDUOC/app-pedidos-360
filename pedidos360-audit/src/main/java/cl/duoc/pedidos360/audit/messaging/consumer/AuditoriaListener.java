package cl.duoc.pedidos360.audit.messaging.consumer;

import cl.duoc.pedidos360.audit.messaging.event.EventoAuditoria;
import cl.duoc.pedidos360.audit.messaging.event.EventoPedido;
import cl.duoc.pedidos360.audit.model.RegistroAuditoria;
import cl.duoc.pedidos360.audit.service.AuditoriaService;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * Consumidores de ms-audit (consumer group audit-service). Dos tópicos, dos listeners:
 * <ul>
 *   <li>{@code audit.timeline}: acciones de usuarios (quién, desde dónde).</li>
 *   <li>{@code orders.events}: los mismos hechos que lee ms-report, pero con OTRO consumer group. Kafka entrega
 *       cada mensaje una vez por grupo: report y audit reciben todo, sin quitarse mensajes.</li>
 * </ul>
 * Ambos listeners pertenecen al grupo audit-service (idIsGroup=false: el id solo nombra el contenedor).
 * Cada listener indica a qué DTO deserializar (propiedad spring.json.value.default.type).
 * La lógica vive en {@link AuditoriaService}; aquí solo se valida y se traduce el mensaje.
 */
@Component
public class AuditoriaListener {

    private static final Logger log = LoggerFactory.getLogger(AuditoriaListener.class);

    private final AuditoriaService auditoriaService;
    private final Validator validator;

    public AuditoriaListener(AuditoriaService auditoriaService, Validator validator) {
        this.auditoriaService = auditoriaService;
        this.validator = validator;
    }

    @KafkaListener(id = "audit-timeline", idIsGroup = false, topics = "${app.kafka.topicos.auditoria}",
            properties = "spring.json.value.default.type=cl.duoc.pedidos360.audit.messaging.event.EventoAuditoria")
    public void onAuditoria(ConsumerRecord<String, EventoAuditoria> mensaje) {
        if (esLapida(mensaje)) return;
        EventoAuditoria e = validar(mensaje.value(), EventoAuditoria.VERSION_SOPORTADA, mensaje.value().version());
        log.info("[RECIBIDO] {} {} {}#{} por {} particion={} offset={}", mensaje.topic(), e.accion(), e.entidad(),
                e.entidadId(), e.actorId(), mensaje.partition(), mensaje.offset());
        auditoriaService.registrar(new RegistroAuditoria(e.eventoId(), mensaje.topic(), e.ocurridoEn(), e.accion(),
                e.entidad(), e.entidadId(), e.detalle(), e.actorId(), e.actorNombre(),
                e.actorRoles() == null ? null : String.join(",", e.actorRoles()),
                e.ip(), e.userAgent(), e.origen(), e.traceId(), e.correlationId()));
    }

    @KafkaListener(id = "audit-pedidos", idIsGroup = false, topics = "${app.kafka.topicos.pedidos}",
            properties = "spring.json.value.default.type=cl.duoc.pedidos360.audit.messaging.event.EventoPedido")
    public void onPedido(ConsumerRecord<String, EventoPedido> mensaje) {
        if (esLapida(mensaje)) return;
        EventoPedido e = validar(mensaje.value(), EventoPedido.VERSION_SOPORTADA, mensaje.value().version());
        log.info("[RECIBIDO] {} {} pedido={} particion={} offset={}", mensaje.topic(), e.tipo(), e.pedidoId(),
                mensaje.partition(), mensaje.offset());
        String detalle = e.estadoAnterior() == null ? "Estado inicial " + e.estado()
                : e.estadoAnterior() + " → " + e.estado();
        auditoriaService.registrar(new RegistroAuditoria(e.eventoId(), mensaje.topic(), e.ocurridoEn(), e.tipo(),
                "PEDIDO", String.valueOf(e.pedidoId()), detalle, e.actorId(), null, null,
                null, null, e.origen(), e.traceId(), e.correlationId()));
    }

    /** Valor nulo = tombstone (marca de borrado en un tópico compactado): no hay nada que auditar. */
    private static boolean esLapida(ConsumerRecord<String, ?> mensaje) {
        if (mensaje.value() == null) {
            log.info("[TOMBSTONE] {} key={} offset={} ignorado", mensaje.topic(), mensaje.key(), mensaje.offset());
            return true;
        }
        return false;
    }

    private <T> T validar(T evento, int versionSoportada, int version) {
        Set<ConstraintViolation<T>> errores = validator.validate(evento);
        if (!errores.isEmpty()) {
            throw new EventoInvalidoException("Evento incompleto: " + errores.stream()
                    .map(v -> v.getPropertyPath() + " " + v.getMessage()).sorted().collect(Collectors.joining(", ")));
        }
        if (version != versionSoportada) {
            throw new EventoInvalidoException("Versión de evento no soportada: " + version);
        }
        return evento;
    }
}
