package cl.duoc.pedidos360.report;

import cl.duoc.pedidos360.report.repository.PedidoProyeccionRepository;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Prueba punta a punta con un broker Kafka embebido: un evento válido actualiza la proyección y un mensaje
 * defectuoso termina en el DLT sin detener al consumidor.
 */
@SpringBootTest(properties = {
        "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
        "spring.datasource.url=jdbc:h2:mem:report;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "app.kafka.reintentos.espera-inicial-ms=100"})
@EmbeddedKafka(partitions = 3, topics = {"orders.events", "orders.events.report.DLT"})
class FlujoKafkaIntegracionTest {

    @Autowired
    private EmbeddedKafkaBroker broker;
    @Autowired
    private PedidoProyeccionRepository proyecciones;

    private KafkaTemplate<String, String> productorCrudo() {
        return new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(
                KafkaTestUtils.producerProps(broker), new StringSerializer(), new StringSerializer()));
    }

    @Test
    void eventoValidoActualizaLaProyeccion() {
        String json = """
                {"eventoId":"e-100","tipo":"OrderCreated","version":1,"ocurridoEn":"%s","origen":"ms-pedidos",
                 "pedidoId":100,"clienteNombre":"Ana","total":12500.0,"estado":"CREADO",
                 "fechaCreacion":"%s","campoNuevoQueNoConozco":"se ignora"}
                """.formatted(Instant.now(), LocalDateTime.now());

        productorCrudo().send("orders.events", "100", json);

        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                assertThat(proyecciones.findById(100L)).get().extracting("estado").isEqualTo("CREADO"));
    }

    @Test
    void mensajeDefectuosoTerminaEnElDltConSuDiagnostico() {
        productorCrudo().send(new ProducerRecord<>("orders.events", "200", "esto no es JSON"));
        productorCrudo().send("orders.events", "201", """
                {"eventoId":"e-201","tipo":"OrderCreated","version":1,"pedidoId":201}""");   // faltan campos

        Map<String, Object> props = KafkaTestUtils.consumerProps("verificador-dlt", "true", broker);
        props.put("auto.offset.reset", "earliest");
        try (Consumer<String, String> consumidor = new DefaultKafkaConsumerFactory<>(props,
                new StringDeserializer(), new StringDeserializer()).createConsumer()) {
            broker.consumeFromAnEmbeddedTopic(consumidor, "orders.events.report.DLT");
            var registros = KafkaTestUtils.getRecords(consumidor, Duration.ofSeconds(20), 2);

            assertThat(registros.count()).isEqualTo(2);
            for (ConsumerRecord<String, String> r : registros) {
                assertThat(r.headers().lastHeader(KafkaHeaders.DLT_ORIGINAL_TOPIC)).isNotNull();
                assertThat(r.headers().lastHeader(KafkaHeaders.DLT_EXCEPTION_MESSAGE)).isNotNull();
            }
        }
        assertThat(proyecciones.findById(201L)).isEmpty();
    }
}
