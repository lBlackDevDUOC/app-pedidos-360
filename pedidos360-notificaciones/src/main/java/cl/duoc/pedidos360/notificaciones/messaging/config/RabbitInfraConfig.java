package cl.duoc.pedidos360.notificaciones.messaging.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.support.converter.ClassMapper;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.boot.autoconfigure.amqp.RabbitTemplateCustomizer;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Infraestructura común de mensajería: formato JSON de los mensajes publicados y seguimiento de las
 * confirmaciones del broker. La conexión, el ACK manual y el prefetch se configuran en application.yml.
 *
 * <p>El conversor JSON se aplica solo al RabbitTemplate (productor). Los consumidores reciben el mensaje
 * crudo y lo convierten dentro de su try/catch con {@code MensajeLector}, para que un mensaje inválido
 * también pase por el manejo explícito de ACK/NACK.</p>
 */
@Configuration
@EnableConfigurationProperties(RabbitProperties.class)
public class RabbitInfraConfig {

    private static final Logger log = LoggerFactory.getLogger(RabbitInfraConfig.class);

    /**
     * Productor: JSON y mensajes con confirmación del broker (publisher confirms) y aviso si un
     * mensaje no calzó con ninguna cola (returns). Ambos casos quedan en el log.
     */
    @Bean
    public RabbitTemplateCustomizer configuracionProductor(ObjectMapper objectMapper) {
        return template -> {
            template.setMessageConverter(conversorJsonSinTipo(objectMapper));
            template.setConfirmCallback((correlacion, ack, causa) -> {
                String id = correlacion != null ? correlacion.getId() : "sin-id";
                if (ack) {
                    log.debug("[CONFIRM] Broker confirmó el mensaje {}", id);
                } else {
                    log.error("[CONFIRM-NACK] Broker rechazó el mensaje {}: {}", id, causa);
                }
            });
            template.setReturnsCallback(devuelto -> log.error(
                    "[SIN-RUTA] Mensaje no enrutado: exchange={} routingKey={} motivo={}",
                    devuelto.getExchange(), devuelto.getRoutingKey(), devuelto.getReplyText()));
        };
    }

    /**
     * JSON sin el header {@code __TypeId__} (nombre de la clase Java del productor). El contrato entre
     * microservicios es el JSON: cada uno tiene su propia clase de evento, en su propio paquete.
     */
    public static Jackson2JsonMessageConverter conversorJsonSinTipo(ObjectMapper objectMapper) {
        Jackson2JsonMessageConverter conversor = new Jackson2JsonMessageConverter(objectMapper);
        conversor.setClassMapper(new ClassMapper() {
            @Override
            public void fromClass(Class<?> clazz, MessageProperties properties) {
                // no se publica el nombre de la clase
            }

            @Override
            public Class<?> toClass(MessageProperties properties) {
                return Object.class;
            }
        });
        return conversor;
    }
}
