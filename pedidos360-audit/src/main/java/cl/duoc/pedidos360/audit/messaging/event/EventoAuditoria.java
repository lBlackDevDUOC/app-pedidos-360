package cl.duoc.pedidos360.audit.messaging.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.List;

/** Contrato de ms-audit para {@code audit.timeline}: quién (actor), qué (accion/detalle), cuándo, desde dónde (ip). */
@JsonIgnoreProperties(ignoreUnknown = true)
public record EventoAuditoria(
        @NotBlank String eventoId,
        int version,
        @NotNull Instant ocurridoEn,
        String origen,
        String traceId,
        String correlationId,
        @NotBlank String accion,
        @NotBlank String entidad,
        @NotBlank String entidadId,
        String detalle,
        @NotBlank String actorId,
        String actorNombre,
        List<String> actorRoles,
        String ip,
        String userAgent) {

    public static final int VERSION_SOPORTADA = 1;
}
