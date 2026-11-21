package cl.duoc.pedidos360.report.messaging.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.time.Instant;
import java.time.LocalDateTime;

/**
 * DTO propio de ms-report para los mensajes de {@code orders.events}. No se comparte código con ms-pedidos:
 * cada microservicio define el contrato que necesita (solo los campos que usa) y tolera campos nuevos
 * (ignoreUnknown), así el productor puede evolucionar el evento sin romper a este consumidor.
 * Las anotaciones de validación son el control de payload: un evento sin estos datos va al DLT.
 */
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
        String clienteNombre,
        @NotNull @PositiveOrZero Double total,
        String estadoAnterior,
        @NotBlank String estado,
        @NotNull LocalDateTime fechaCreacion,
        LocalDateTime fechaEntrega) {

    /** Versión del esquema que este consumidor sabe interpretar. */
    public static final int VERSION_SOPORTADA = 1;
}
