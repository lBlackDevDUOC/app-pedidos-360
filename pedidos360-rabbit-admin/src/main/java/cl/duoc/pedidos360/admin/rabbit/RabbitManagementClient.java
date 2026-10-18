package cl.duoc.pedidos360.admin.rabbit;

import cl.duoc.pedidos360.admin.config.AdminProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Lecturas a través de la API HTTP del plugin Management de RabbitMQ. AMQP (AmqpAdmin) sirve para crear y
 * eliminar, pero no permite listar recursos ni ver métricas (mensajes listos/sin confirmar, consumidores).
 */
@Component
public class RabbitManagementClient {

    private final RestClient restClient;
    private final String vhost;

    public RabbitManagementClient(RestClient.Builder builder, AdminProperties props) {
        AdminProperties.Management management = props.management();
        this.restClient = builder
                .baseUrl(management.url())
                .defaultHeaders(headers -> headers.setBasicAuth(management.usuario(), management.clave()))
                .build();
        this.vhost = management.vhost();
    }

    public List<ColaInfo> listarColas() {
        return restClient.get().uri("/queues/{vhost}", vhost).retrieve()
                .body(new ParameterizedTypeReference<>() {});
    }

    public Optional<ColaInfo> buscarCola(String nombre) {
        try {
            return Optional.ofNullable(restClient.get().uri("/queues/{vhost}/{nombre}", vhost, nombre)
                    .retrieve().body(ColaInfo.class));
        } catch (HttpClientErrorException.NotFound e) {
            return Optional.empty();
        }
    }

    public List<ExchangeInfo> listarExchanges() {
        return restClient.get().uri("/exchanges/{vhost}", vhost).retrieve()
                .body(new ParameterizedTypeReference<>() {});
    }

    public boolean existeExchange(String nombre) {
        try {
            restClient.get().uri("/exchanges/{vhost}/{nombre}", vhost, nombre).retrieve().toBodilessEntity();
            return true;
        } catch (HttpClientErrorException.NotFound e) {
            return false;
        }
    }

    public List<BindingInfo> listarBindings() {
        return restClient.get().uri("/bindings/{vhost}", vhost).retrieve()
                .body(new ParameterizedTypeReference<>() {});
    }

    /** Cola tal como la reporta la API de Management (las métricas pueden faltar recién creada la cola). */
    public record ColaInfo(
            String name,
            boolean durable,
            Long messages,
            @JsonProperty("messages_ready") Long messagesReady,
            @JsonProperty("messages_unacknowledged") Long messagesUnacknowledged,
            Integer consumers,
            Map<String, Object> arguments) {
    }

    public record ExchangeInfo(String name, String type, boolean durable) {
    }

    public record BindingInfo(
            String source,
            String destination,
            @JsonProperty("destination_type") String destinationType,
            @JsonProperty("routing_key") String routingKey) {
    }
}
