package cl.duoc.pedidos360.kafkaadmin;

import cl.duoc.pedidos360.kafkaadmin.dto.Dtos.ActualizarConfigsRequest;
import cl.duoc.pedidos360.kafkaadmin.dto.Dtos.CrearTopicoRequest;
import cl.duoc.pedidos360.kafkaadmin.dto.Dtos.PuntoReset;
import cl.duoc.pedidos360.kafkaadmin.dto.Dtos.ResetOffsetsRequest;
import cl.duoc.pedidos360.kafkaadmin.service.AdminExceptions.ConfiguracionInvalida;
import cl.duoc.pedidos360.kafkaadmin.service.AdminExceptions.OperacionNoPermitida;
import cl.duoc.pedidos360.kafkaadmin.service.AdminExceptions.RecursoNoEncontrado;
import cl.duoc.pedidos360.kafkaadmin.service.AdminExceptions.RecursoYaExiste;
import cl.duoc.pedidos360.kafkaadmin.service.KafkaAdminService;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Operaciones reales contra un clúster embebido de 3 brokers (igual que el de producción). */
@SpringBootTest(properties = "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}")
@EmbeddedKafka(count = 3, partitions = 3, topics = "orders.events")
class KafkaAdminServiceIntegracionTest {

    @Autowired
    private KafkaAdminService service;
    @Autowired
    private EmbeddedKafkaBroker broker;

    @Test
    void clusterMuestraLosTresBrokers() {
        assertThat(service.cluster().cantidadBrokers()).isEqualTo(3);
        assertThat(service.cluster().brokers()).filteredOn(b -> b.controlador()).hasSize(1);
    }

    @Test
    void cicloDeVidaDeUnTopico() {
        var creado = service.crearTopico(new CrearTopicoRequest("demo.ciclo", 3, (short) 3,
                Map.of("retention.ms", "86400000", "min.insync.replicas", "2")));
        assertThat(creado.particiones()).isEqualTo(3);
        assertThat(creado.factorReplicacion()).isEqualTo(3);
        assertThat(creado.configsModificadas()).containsEntry("retention.ms", "86400000");
        assertThat(creado.detalleParticiones()).allSatisfy(p -> assertThat(p.isr()).hasSize(3));

        assertThatThrownBy(() -> service.crearTopico(new CrearTopicoRequest("demo.ciclo", 3, (short) 3, null)))
                .isInstanceOf(RecursoYaExiste.class);

        var configs = service.actualizarConfigs("demo.ciclo",
                new ActualizarConfigsRequest(Map.of("cleanup.policy", "compact,delete", "retention.ms", "259200000")));
        assertThat(configs).anySatisfy(c -> {
            assertThat(c.nombre()).isEqualTo("cleanup.policy");
            assertThat(c.valor()).isEqualTo("compact,delete");
        });

        assertThat(service.aumentarParticiones("demo.ciclo", 6).particiones()).isEqualTo(6);
        assertThatThrownBy(() -> service.aumentarParticiones("demo.ciclo", 2)).isInstanceOf(ConfiguracionInvalida.class);

        service.eliminarTopico("demo.ciclo");
        assertThatThrownBy(() -> service.obtenerTopico("demo.ciclo")).isInstanceOf(RecursoNoEncontrado.class);
    }

    @Test
    void validacionesQueProtegenElCluster() {
        // más réplicas que brokers
        assertThatThrownBy(() -> service.crearTopico(new CrearTopicoRequest("demo.rf", 3, (short) 4, null)))
                .isInstanceOf(ConfiguracionInvalida.class);
        // min.insync.replicas mayor que las réplicas: todas las escrituras con acks=all fallarían
        assertThatThrownBy(() -> service.actualizarConfigs("orders.events",
                new ActualizarConfigsRequest(Map.of("min.insync.replicas", "5"))))
                .isInstanceOf(ConfiguracionInvalida.class);
        // configuración fuera de la lista blanca
        assertThatThrownBy(() -> service.actualizarConfigs("orders.events",
                new ActualizarConfigsRequest(Map.of("unclean.leader.election.enable", "true"))))
                .isInstanceOf(ConfiguracionInvalida.class);
        // tópico del sistema
        assertThatThrownBy(() -> service.eliminarTopico("orders.events")).isInstanceOf(OperacionNoPermitida.class);
    }

    @Test
    void lagDeUnConsumerGroupYResetDeOffsets() {
        KafkaTemplate<String, String> productor = new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(
                KafkaTestUtils.producerProps(broker), new StringSerializer(), new StringSerializer()));
        for (int i = 0; i < 6; i++) {
            productor.send("orders.events", String.valueOf(i), "evento-" + i);
        }
        productor.flush();

        // Un consumidor lee todo y confirma; luego se publican 4 más que quedan pendientes (lag 4)
        Map<String, Object> props = KafkaTestUtils.consumerProps("grupo-demo", "false", broker);
        props.put("auto.offset.reset", "earliest");
        try (Consumer<String, String> consumidor = new DefaultKafkaConsumerFactory<>(props,
                new StringDeserializer(), new StringDeserializer()).createConsumer()) {
            broker.consumeFromAnEmbeddedTopic(consumidor, "orders.events");
            KafkaTestUtils.getRecords(consumidor, Duration.ofSeconds(10), 6);
            consumidor.commitSync();

            var conConsumidor = service.obtenerGrupo("grupo-demo");
            assertThat(conConsumidor.miembros()).hasSize(1);
            assertThatThrownBy(() -> service.resetearOffsets("grupo-demo",
                    new ResetOffsetsRequest("orders.events", PuntoReset.EARLIEST, null)))
                    .isInstanceOf(OperacionNoPermitida.class);
        }
        for (int i = 0; i < 4; i++) {
            productor.send("orders.events", "k" + i, "pendiente-" + i);
        }
        productor.flush();

        assertThat(service.obtenerGrupo("grupo-demo").lagTotal()).isEqualTo(4);

        var reset = service.resetearOffsets("grupo-demo", new ResetOffsetsRequest("orders.events", PuntoReset.EARLIEST, null));
        assertThat(reset.nuevosOffsets()).allSatisfy(o -> assertThat(o.offsetConfirmado()).isZero());
        assertThat(service.obtenerGrupo("grupo-demo").lagTotal()).isGreaterThanOrEqualTo(10);

        assertThat(service.listarGrupos()).anySatisfy(g -> assertThat(g.groupId()).isEqualTo("grupo-demo"));
    }
}
