package cl.duoc.pedidos360.messaging.config;

import cl.duoc.pedidos360.messaging.event.PedidoCreadoEvent;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regresión: con el header __TypeId__ el consumidor de OTRO microservicio intentaba cargar la clase del
 * productor (que no existe en su código) y fallaba antes de llegar al ACK/NACK.
 */
class ConversorJsonTest {

    @Test
    void publicaJsonSinNombreDeClaseJava() {
        Message mensaje = RabbitInfraConfig.conversorJsonSinTipo(JsonMapper.builder().findAndAddModules().build())
                .toMessage(new PedidoCreadoEvent("e-1", 1L, "Ana", "ana@duoc.cl", 5000.0, "PENDIENTE", null),
                        new MessageProperties());

        assertThat(mensaje.getMessageProperties().getHeaders()).doesNotContainKey("__TypeId__");
        assertThat(mensaje.getMessageProperties().getContentType()).isEqualTo(MessageProperties.CONTENT_TYPE_JSON);
        assertThat(new String(mensaje.getBody(), StandardCharsets.UTF_8)).contains("\"pedidoId\":1");
    }
}
