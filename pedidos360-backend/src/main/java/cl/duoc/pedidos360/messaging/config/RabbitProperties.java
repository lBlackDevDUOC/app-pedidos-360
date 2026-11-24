package cl.duoc.pedidos360.messaging.config;

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
 * Si falta un valor, la aplicación no arranca (validación con {@link Validated}).
 */
@Validated
@ConfigurationProperties(prefix = "app.rabbitmq")
public record RabbitProperties(
        @Valid @NotNull Exchanges exchanges,
        @Valid @NotNull RoutingKeys routingKeys,
        @Valid @NotNull Colas colas,
        @Valid @NotNull Reintentos reintentos,
        @Valid @NotNull Retencion retencion) {

    /** eventos: topic de eventos de negocio; reintentos: hacia colas de reintento; deadLetter: DLX hacia las DLQ. */
    public record Exchanges(@NotBlank String eventos, @NotBlank String reintentos, @NotBlank String deadLetter) {}

    public record RoutingKeys(@NotBlank String pedidoCreado, @NotBlank String documentoGenerado) {}

    /** Colas que consume este microservicio. */
    public record Colas(@Valid @NotNull Cola documentoGenerado) {}

    /** Una cola de trabajo junto a su cola de reintento y su DLQ. */
    public record Cola(@NotBlank String nombre, @NotBlank String reintento, @NotBlank String dlq) {}

    /** maximo: reintentos antes de ir a la DLQ; esperaMs: espera entre intentos (TTL de la cola de reintento). */
    public record Reintentos(@Min(0) int maximo, @Positive int esperaMs) {}

    /** Límites que evitan el crecimiento descontrolado de las colas. */
    public record Retencion(
            @Positive int ttlColaMs,
            @Positive int maxMensajesCola,
            @Positive int ttlDlqMs,
            @Positive int maxMensajesDlq) {}
}
