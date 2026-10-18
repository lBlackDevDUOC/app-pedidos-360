package cl.duoc.pedidos360.messaging.support;

import cl.duoc.pedidos360.messaging.config.RabbitProperties;
import com.rabbitmq.client.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Decide qué hacer con un mensaje que falló por un error RECUPERABLE (BD caída, servicio externo lento...).
 *
 * <ul>
 *   <li>Quedan reintentos: publica una copia en el exchange de reintentos (va a la cola .retry,
 *       espera el TTL y vuelve a la cola de trabajo) y hace ACK del original.</li>
 *   <li>Sin reintentos o si no se pudo programar el reintento: NACK sin re-encolar → el DLX lo deja en la DLQ.</li>
 * </ul>
 * Nunca se usa NACK con requeue=true: re-encolar de inmediato provoca un bucle que satura al consumidor.
 */
@Component
public class ReintentoHandler {

    /** Header propio que cuenta los reintentos ya realizados. */
    public static final String HEADER_REINTENTOS = "x-reintentos";

    private static final Logger log = LoggerFactory.getLogger(ReintentoHandler.class);

    private final RabbitTemplate rabbitTemplate;
    private final RabbitProperties props;

    public ReintentoHandler(RabbitTemplate rabbitTemplate, RabbitProperties props) {
        this.rabbitTemplate = rabbitTemplate;
        this.props = props;
    }

    public void reintentarOEnviarADlq(Message mensaje, Channel canal, RabbitProperties.Cola cola, Exception causa)
            throws IOException {
        long deliveryTag = mensaje.getMessageProperties().getDeliveryTag();
        int realizados = reintentosRealizados(mensaje);
        int maximo = props.reintentos().maximo();

        if (realizados >= maximo) {
            log.error("[DLQ] {} agotó {} reintentos, se envía a {}. Causa: {}",
                    MensajeLector.resumen(mensaje), maximo, cola.dlq(), causa.getMessage());
            canal.basicNack(deliveryTag, false, false);
            return;
        }

        Message copia = MessageBuilder.fromClonedMessage(mensaje)
                .setHeader(HEADER_REINTENTOS, realizados + 1)
                .setDeliveryMode(MessageDeliveryMode.PERSISTENT)
                .build();
        try {
            rabbitTemplate.send(props.exchanges().reintentos(), cola.nombre(), copia);
            canal.basicAck(deliveryTag, false);
            log.warn("[REINTENTO {}/{}] {} falló ({}), se reintenta en {} ms",
                    realizados + 1, maximo, MensajeLector.resumen(mensaje), causa.getMessage(),
                    props.reintentos().esperaMs());
        } catch (AmqpException e) {
            log.error("[DLQ] No se pudo programar el reintento de {}, se envía a {}: {}",
                    MensajeLector.resumen(mensaje), cola.dlq(), e.getMessage());
            canal.basicNack(deliveryTag, false, false);
        }
    }

    static int reintentosRealizados(Message mensaje) {
        Object valor = mensaje.getMessageProperties().getHeader(HEADER_REINTENTOS);
        return valor instanceof Number numero ? numero.intValue() : 0;
    }
}
