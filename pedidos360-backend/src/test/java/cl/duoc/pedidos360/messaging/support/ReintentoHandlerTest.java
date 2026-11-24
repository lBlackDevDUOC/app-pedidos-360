package cl.duoc.pedidos360.messaging.support;

import cl.duoc.pedidos360.messaging.PropiedadesDePrueba;
import cl.duoc.pedidos360.messaging.config.RabbitProperties;
import com.rabbitmq.client.Channel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.AmqpConnectException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.net.ConnectException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class ReintentoHandlerTest {

    private static final long TAG = 9L;

    private final RabbitProperties props = PropiedadesDePrueba.crear();
    private final RabbitProperties.Cola cola = props.colas().documentoGenerado();
    private RabbitTemplate rabbitTemplate;
    private Channel canal;
    private ReintentoHandler handler;

    @BeforeEach
    void preparar() {
        rabbitTemplate = mock(RabbitTemplate.class);
        canal = mock(Channel.class);
        handler = new ReintentoHandler(rabbitTemplate, props);
    }

    @Test
    void primerFallo_publicaEnReintentosConContador1YHaceAck() throws Exception {
        handler.reintentarOEnviarADlq(mensajeConReintentos(null), canal, cola, new RuntimeException("timeout"));

        ArgumentCaptor<Message> copia = ArgumentCaptor.forClass(Message.class);
        verify(rabbitTemplate).send(eq("test.retry"), eq("test.q"), copia.capture());
        assertThat((Integer) copia.getValue().getMessageProperties().getHeader(ReintentoHandler.HEADER_REINTENTOS))
                .isEqualTo(1);
        verify(canal).basicAck(TAG, false);
        verify(canal, never()).basicNack(anyLong(), anyBoolean(), anyBoolean());
    }

    @Test
    void reintentosAgotados_haceNackSinReencolarParaIrALaDlq() throws Exception {
        handler.reintentarOEnviarADlq(mensajeConReintentos(3), canal, cola, new RuntimeException("timeout"));

        verify(canal).basicNack(TAG, false, false);
        verify(rabbitTemplate, never()).send(anyString(), anyString(), any(Message.class));
        verify(canal, never()).basicAck(anyLong(), anyBoolean());
    }

    @Test
    void siNoSePuedeProgramarElReintento_vaALaDlq() throws Exception {
        doThrow(new AmqpConnectException(new ConnectException("broker caído")))
                .when(rabbitTemplate).send(anyString(), anyString(), any(Message.class));

        handler.reintentarOEnviarADlq(mensajeConReintentos(1), canal, cola, new RuntimeException("timeout"));

        verify(canal).basicNack(TAG, false, false);
        verify(canal, never()).basicAck(anyLong(), anyBoolean());
    }

    private static Message mensajeConReintentos(Integer reintentos) {
        MessageProperties propiedades = new MessageProperties();
        propiedades.setDeliveryTag(TAG);
        if (reintentos != null) {
            propiedades.setHeader(ReintentoHandler.HEADER_REINTENTOS, reintentos);
        }
        return new Message("{}".getBytes(), propiedades);
    }
}
