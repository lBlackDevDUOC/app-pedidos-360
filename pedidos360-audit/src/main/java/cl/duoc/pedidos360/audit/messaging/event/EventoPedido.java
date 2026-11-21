package cl.duoc.pedidos360.audit.messaging.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

/** Contrato de ms-audit para {@code orders.events}: solo los campos que necesita la línea de tiempo. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record EventoPedido(
        @NotBlank String eventoId,
        @NotBlank String tipo,
        int version,
        @NotNull Instant ocurridoEn,
        String origen,
        String traceId,
        String correlationId,
        @NotNull Long pedidoId,
        String estadoAnterior,
        @NotBlank String estado,
        String actorId) {

    public static final int VERSION_SOPORTADA = 1;
}
