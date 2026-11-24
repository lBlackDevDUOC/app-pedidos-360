package cl.duoc.pedidos360.messaging.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.util.Map;

/**
 * Configuración del productor Kafka.
 * <ul>
 *   <li>Conexión y comportamiento (bootstrap servers, acks=all, idempotencia, compresión, timeouts) vienen de
 *       {@code spring.kafka.*} en application.yml.</li>
 *   <li>Key: String (id del pedido o del evento). Value: JSON con el ObjectMapper de Spring Boot, así las fechas
 *       viajan en ISO-8601 ("2026-11-20T22:19:58") y cualquier consumidor (Java o no) las entiende.</li>
 *   <li>Sin headers de tipo Java (__TypeId__): cada consumidor deserializa a su propio DTO.</li>
 * </ul>
 * Los tópicos NO se crean desde aquí (no hay beans NewTopic): el clúster tiene auto.create.topics.enable=false y
 * se crean de forma controlada con crear-topicos.sh o ms-kafka-admin.
 */
@Configuration
@EnableConfigurationProperties(KafkaTopicosProperties.class)
public class KafkaProducerConfig {

    @Bean
    public ProducerFactory<String, Object> producerFactory(KafkaProperties kafkaProperties, ObjectMapper objectMapper) {
        Map<String, Object> propiedades = kafkaProperties.buildProducerProperties(null);
        JsonSerializer<Object> valorJson = new JsonSerializer<>(objectMapper);
        valorJson.setAddTypeInfo(false);
        return new DefaultKafkaProducerFactory<>(propiedades, new StringSerializer(), valorJson);
    }

    @Bean
    public KafkaTemplate<String, Object> kafkaTemplate(ProducerFactory<String, Object> producerFactory) {
        return new KafkaTemplate<>(producerFactory);
    }
}
