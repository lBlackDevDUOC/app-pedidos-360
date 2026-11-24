package cl.duoc.pedidos360.kafkaadmin.config;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.List;

/** Bloque {@code app.kafka-admin} de application.yml. */
@Validated
@ConfigurationProperties(prefix = "app.kafka-admin")
public record AdminProperties(
        @Positive long timeoutMs,
        List<String> topicosProtegidos,
        List<String> sufijosProtegidos,
        @NotEmpty List<String> configsEditables,
        @Positive int maxParticiones) {

    public AdminProperties {
        topicosProtegidos = topicosProtegidos == null ? List.of() : List.copyOf(topicosProtegidos);
        sufijosProtegidos = sufijosProtegidos == null ? List.of() : List.copyOf(sufijosProtegidos);
    }

    /** Del sistema (no se borran): los de la lista, los DLT y los internos de Kafka ("__consumer_offsets"). */
    public boolean esProtegido(String topico) {
        return topico.startsWith("__")
                || topicosProtegidos.contains(topico)
                || sufijosProtegidos.stream().anyMatch(topico::endsWith);
    }
}
