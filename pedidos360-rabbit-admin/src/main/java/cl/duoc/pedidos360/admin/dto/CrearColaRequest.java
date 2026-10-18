package cl.duoc.pedidos360.admin.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

/** Cuerpo de POST /api/admin/queues. Solo el nombre es obligatorio. */
public record CrearColaRequest(
        @Schema(example = "demo.reportes.q")
        @NotBlank(message = "es obligatorio")
        @Pattern(regexp = ReglasNombres.PATRON_NOMBRE, message = ReglasNombres.MENSAJE_NOMBRE)
        String nombre,

        @Schema(description = "Sobrevive a reinicios de RabbitMQ. Por defecto true.")
        Boolean durable,

        @Schema(description = "Tiempo de vida de cada mensaje (x-message-ttl), máx. 7 días", example = "60000")
        @Positive(message = "debe ser mayor a 0")
        @Max(value = 604_800_000, message = "no puede superar 7 días (604800000 ms)")
        Integer ttlMs,

        @Schema(description = "Largo máximo de la cola (x-max-length)", example = "1000")
        @Positive(message = "debe ser mayor a 0")
        @Max(value = 1_000_000, message = "no puede superar 1.000.000 mensajes")
        Integer maxMensajes,

        @Schema(description = "Exchange al que van los mensajes muertos (x-dead-letter-exchange)", example = "pedidos360.dlx")
        @Pattern(regexp = ReglasNombres.PATRON_NOMBRE, message = ReglasNombres.MENSAJE_NOMBRE)
        String deadLetterExchange,

        @Schema(description = "Routing key para los mensajes muertos (requiere deadLetterExchange)")
        @Pattern(regexp = ReglasNombres.PATRON_ROUTING_KEY, message = ReglasNombres.MENSAJE_ROUTING_KEY)
        String deadLetterRoutingKey) {

    public boolean esDurable() {
        return durable == null || durable;
    }

    /** Una routing key de DLX sin exchange DLX es una configuración inválida. */
    @JsonIgnore
    @AssertTrue(message = "deadLetterRoutingKey requiere indicar deadLetterExchange")
    public boolean isDeadLetterConsistente() {
        return deadLetterRoutingKey == null || deadLetterExchange != null;
    }
}
