package cl.duoc.pedidos360.messaging.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Nombres de tópicos Kafka, leídos del bloque {@code app.kafka} de application.yml. Igual que con RabbitMQ,
 * ninguna clase escribe un nombre de tópico a mano. Si falta uno, la aplicación no arranca.
 * (La conexión: brokers, serializers, acks, etc. la configura Spring Boot desde {@code spring.kafka}.)
 */
@Validated
@ConfigurationProperties(prefix = "app.kafka")
public record KafkaTopicosProperties(@Valid @NotNull Topicos topicos) {

    /** pedidos: flujo canónico de eventos de negocio; auditoria: línea de tiempo de auditoría. */
    public record Topicos(@NotBlank String pedidos, @NotBlank String auditoria) {}
}
