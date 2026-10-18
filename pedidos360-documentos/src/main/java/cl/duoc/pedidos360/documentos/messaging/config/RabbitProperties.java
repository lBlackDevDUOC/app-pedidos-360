package cl.duoc.pedidos360.documentos.messaging.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Topología de mensajería del microservicio, leída del bloque {@code app.rabbitmq} de application.yml.
 * Es la única fuente de nombres de exchanges, colas y routing keys: ninguna clase los escribe a mano.
 */
@Validated
@ConfigurationProperties(prefix = "app.rabbitmq")
public record RabbitProperties(
        @Valid @NotNull Exchanges exchanges,
        @Valid @NotNull RoutingKeys routingKeys,
        @Valid @NotNull Colas colas,
        @Valid @NotNull Reintentos reintentos,
        @Valid @NotNull Retencion retencion) {

    public record Exchanges(@NotBlank String eventos, @NotBlank String reintentos, @NotBlank String deadLetter) {}

    public record RoutingKeys(@NotBlank String pedidoCreado, @NotBlank String documentoGenerado) {}

    /** Colas que consume este microservicio. */
    public record Colas(@Valid @NotNull Cola pedidoCreado) {}

    /** Una cola de trabajo junto a su cola de reintento y su DLQ. */
    public record Cola(@NotBlank String nombre, @NotBlank String reintento, @NotBlank String dlq) {}

    public record Reintentos(@Min(0) int maximo, @Positive int esperaMs) {}

    public record Retencion(
            @Positive int ttlColaMs,
            @Positive int maxMensajesCola,
            @Positive int ttlDlqMs,
            @Positive int maxMensajesDlq) {}
}
