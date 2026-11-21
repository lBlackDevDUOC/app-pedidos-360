package cl.duoc.pedidos360.audit;

import cl.duoc.pedidos360.audit.model.RegistroAuditoria;
import cl.duoc.pedidos360.audit.repository.RegistroAuditoriaRepository;
import cl.duoc.pedidos360.audit.service.AuditoriaService;
import org.apache.kafka.clients.consumer.Consumer;
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

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Punta a punta con Kafka embebido: los dos tópicos alimentan la misma línea de tiempo, la búsqueda filtra por
 * usuario y un evento defectuoso termina en el DLT de su tópico de origen.
 */
@SpringBootTest(properties = {
        "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
        "spring.datasource.url=jdbc:h2:mem:audit;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "app.kafka.reintentos.espera-inicial-ms=100"})
@EmbeddedKafka(partitions = 3, topics = {"audit.timeline", "orders.events",
        "audit.timeline.audit.DLT", "orders.events.audit.DLT"})
class FlujoAuditoriaIntegracionTest {

    @Autowired
    private EmbeddedKafkaBroker broker;
    @Autowired
    private RegistroAuditoriaRepository repositorio;
    @Autowired
    private AuditoriaService service;

    private KafkaTemplate<String, String> productor() {
        return new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(
                KafkaTestUtils.producerProps(broker), new StringSerializer(), new StringSerializer()));
    }

    @Test
    void ambosTopicosAlimentanLaLineaDeTiempoDelPedido() {
        Instant t = Instant.parse("2026-11-20T15:00:00Z");
        productor().send("audit.timeline", "a-1", """
                {"eventoId":"a-1","version":1,"ocurridoEn":"%s","accion":"PEDIDO_ESTADO_CAMBIADO","entidad":"PEDIDO",
                 "entidadId":"77","detalle":"CREADO → ACEPTADO","actorId":"oid-op","actorNombre":"Olga Operadora",
                 "actorRoles":["Operador"],"ip":"190.5.5.5","userAgent":"Chrome"}""".formatted(t.plusSeconds(1)));
        productor().send("orders.events", "77", """
                {"eventoId":"p-1","tipo":"OrderAccepted","version":1,"ocurridoEn":"%s","pedidoId":77,
                 "estadoAnterior":"CREADO","estado":"ACEPTADO","actorId":"oid-op","total":1000}""".formatted(t));

        await().atMost(Duration.ofSeconds(20)).until(() -> service.timelinePedido(77L).size() == 2);

        var timeline = service.timelinePedido(77L);
        assertThat(timeline).extracting(RegistroAuditoria::getFuente).containsExactly("orders.events", "audit.timeline");
        assertThat(timeline.get(1).getIp()).isEqualTo("190.5.5.5");
        assertThat(service.buscar("olga", null, null, null, null, 0, 10).getContent())
                .extracting(RegistroAuditoria::getEventoId).containsExactly("a-1");
    }

    @Test
    void eventoSinActorVaAlDltDeSuTopico() {
        productor().send("audit.timeline", "a-mal", """
                {"eventoId":"a-mal","version":1,"ocurridoEn":"2026-11-20T15:00:00Z","accion":"X","entidad":"PEDIDO",
                 "entidadId":"1"}""");

        Map<String, Object> props = KafkaTestUtils.consumerProps("verificador-dlt", "true", broker);
        props.put("auto.offset.reset", "earliest");
        try (Consumer<String, String> consumidor = new DefaultKafkaConsumerFactory<>(props,
                new StringDeserializer(), new StringDeserializer()).createConsumer()) {
            broker.consumeFromAnEmbeddedTopic(consumidor, "audit.timeline.audit.DLT");
            var registro = KafkaTestUtils.getSingleRecord(consumidor, "audit.timeline.audit.DLT", Duration.ofSeconds(20));

            assertThat(registro.key()).isEqualTo("a-mal");
            String causa = new String(registro.headers().lastHeader(KafkaHeaders.DLT_EXCEPTION_MESSAGE).value(),
                    StandardCharsets.UTF_8);
            assertThat(causa).contains("actorId");
        }
        assertThat(repositorio.existsById("a-mal")).isFalse();
    }
}
