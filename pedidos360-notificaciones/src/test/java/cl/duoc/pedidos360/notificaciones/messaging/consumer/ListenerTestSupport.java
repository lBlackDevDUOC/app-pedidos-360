package cl.duoc.pedidos360.notificaciones.messaging.consumer;

import cl.duoc.pedidos360.notificaciones.messaging.config.RabbitProperties;
import cl.duoc.pedidos360.notificaciones.messaging.support.MensajeLector;
import com.fasterxml.jackson.databind.json.JsonMapper;
import jakarta.validation.Validation;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;

import java.nio.charset.StandardCharsets;

/** Utilidades comunes para probar consumidores sin levantar Spring ni RabbitMQ. */
public final class ListenerTestSupport {

    public static final long TAG = 3L;

    private ListenerTestSupport() {
    }

    public static RabbitProperties props() {
        return new RabbitProperties(
                new RabbitProperties.Exchanges("t.events", "t.retry", "t.dlx"),
                new RabbitProperties.RoutingKeys("pedido.creado", "documento.generado"),
                new RabbitProperties.Colas(
                        new RabbitProperties.Cola("t.pedido.q", "t.pedido.retry", "t.pedido.dlq"),
                        new RabbitProperties.Cola("t.doc.q", "t.doc.retry", "t.doc.dlq")),
                new RabbitProperties.Reintentos(3, 5000),
                new RabbitProperties.Retencion(600000, 1000, 86400000, 5000));
    }

    public static MensajeLector lector() {
        return new MensajeLector(
                JsonMapper.builder().findAndAddModules().build(),
                Validation.buildDefaultValidatorFactory().getValidator());
    }

    public static Message mensaje(String json) {
        MessageProperties propiedades = new MessageProperties();
        propiedades.setDeliveryTag(TAG);
        return new Message(json.getBytes(StandardCharsets.UTF_8), propiedades);
    }
}
