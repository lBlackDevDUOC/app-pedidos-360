package cl.duoc.pedidos360.report.messaging.config;

import cl.duoc.pedidos360.report.messaging.consumer.EventoInvalidoException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.Serializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.DelegatingByTypeSerializer;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.util.backoff.ExponentialBackOff;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Manejo de errores del consumidor. El consumidor (group, deserializers, concurrency, ack-mode) lo arma Spring
 * Boot desde {@code spring.kafka.consumer.*}; Boot le aplica automáticamente el {@link CommonErrorHandler} de aquí.
 *
 * <p>Estrategia ante un evento que falla:
 * <ol>
 *   <li><b>Error transitorio</b> (ej. la BD no responde): se reintenta con backoff exponencial 1 s, 2 s, 4 s.
 *       Mientras tanto el consumidor no avanza en esa partición, así no se pierde el orden de los eventos.</li>
 *   <li><b>Error permanente</b> (JSON inválido, campos faltantes, versión desconocida): reintentar no sirve,
 *       va directo al DLT.</li>
 *   <li>Agotados los reintentos, el mensaje original se publica en {@code orders.events.report.DLT} (misma
 *       partición) con headers de diagnóstico que agrega Spring: tópico, partición y offset de origen, clase y
 *       mensaje de la excepción y stacktrace. El consumidor confirma el offset y sigue con el siguiente mensaje:
 *       un mensaje defectuoso nunca detiene el flujo.</li>
 * </ol>
 */
@Configuration
public class KafkaConsumerConfig {

    private static final Logger log = LoggerFactory.getLogger(KafkaConsumerConfig.class);

    /**
     * Productor exclusivo para el DLT. Si el mensaje ni siquiera se pudo deserializar llega como byte[] y se
     * reenvía tal cual; si se deserializó pero falló el procesamiento, se reenvía como JSON.
     */
    @Bean
    @SuppressWarnings({"unchecked", "rawtypes"})
    public KafkaTemplate<Object, Object> dltKafkaTemplate(KafkaProperties kafkaProperties, ObjectMapper objectMapper) {
        JsonSerializer<Object> json = new JsonSerializer<>(objectMapper);
        json.setAddTypeInfo(false);
        Map<Class<?>, Serializer<?>> porTipo = new LinkedHashMap<>();
        porTipo.put(byte[].class, new ByteArraySerializer());
        porTipo.put(Object.class, json);

        Map<String, Object> propiedades = kafkaProperties.buildProducerProperties(null);
        var factory = new DefaultKafkaProducerFactory<Object, Object>(propiedades,
                (Serializer) new StringSerializer(), new DelegatingByTypeSerializer(porTipo, true));
        return new KafkaTemplate<>(factory);
    }

    @Bean
    public CommonErrorHandler kafkaErrorHandler(KafkaTemplate<Object, Object> dltKafkaTemplate, AppKafkaProperties props) {
        String dlt = props.topicos().dlt();
        DeadLetterPublishingRecoverer haciaDlt = new DeadLetterPublishingRecoverer(dltKafkaTemplate,
                (registro, error) -> new TopicPartition(dlt, registro.partition()));

        ExponentialBackOff backOff = new ExponentialBackOff(
                props.reintentos().esperaInicialMs(), props.reintentos().multiplicador());
        backOff.setMaxAttempts(props.reintentos().maximo());

        DefaultErrorHandler handler = new DefaultErrorHandler((registro, error) -> {
            log.error("[DLT] {} topico={} particion={} offset={} key={} → {}. Causa: {}",
                    descripcion(registro), registro.topic(), registro.partition(), registro.offset(),
                    registro.key(), dlt, causaRaiz(error));
            haciaDlt.accept(registro, error);
        }, backOff);

        // Errores de datos: reintentar daría siempre el mismo resultado (DeserializationException ya viene incluida)
        handler.addNotRetryableExceptions(EventoInvalidoException.class, IllegalArgumentException.class);

        handler.setRetryListeners((registro, error, intento) ->
                log.warn("[FALLO intento {} de {}] topico={} particion={} offset={}: {}", intento, props.reintentos().maximo(),
                        registro.topic(), registro.partition(), registro.offset(), causaRaiz(error)));
        return handler;
    }

    private static String descripcion(ConsumerRecord<?, ?> registro) {
        return registro.value() == null ? "mensaje ilegible" : registro.value().getClass().getSimpleName();
    }

    private static String causaRaiz(Throwable error) {
        Throwable causa = error;
        while (causa.getCause() != null && causa.getCause() != causa) {
            causa = causa.getCause();
        }
        return causa.getClass().getSimpleName() + ": " + causa.getMessage();
    }
}
