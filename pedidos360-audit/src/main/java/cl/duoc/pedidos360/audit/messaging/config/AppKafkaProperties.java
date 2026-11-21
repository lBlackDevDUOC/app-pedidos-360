package cl.duoc.pedidos360.audit.messaging.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Bloque {@code app.kafka} de application.yml: nombres de tópicos y política de reintentos. */
@Validated
@ConfigurationProperties(prefix = "app.kafka")
public record AppKafkaProperties(@Valid @NotNull Topicos topicos, @Valid @NotNull Reintentos reintentos) {

    public record Topicos(@NotBlank String auditoria, @NotBlank String pedidos, @NotBlank String sufijoDlt) {}

    public record Reintentos(@Min(0) int maximo, @Positive long esperaInicialMs, @DecimalMin("1.0") double multiplicador) {}
}
