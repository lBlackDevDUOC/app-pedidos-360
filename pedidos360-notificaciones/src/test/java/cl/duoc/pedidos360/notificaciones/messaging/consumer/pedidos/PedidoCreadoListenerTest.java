package cl.duoc.pedidos360.notificaciones.messaging.consumer.pedidos;

import cl.duoc.pedidos360.notificaciones.messaging.config.RabbitProperties;
import cl.duoc.pedidos360.notificaciones.messaging.consumer.ListenerTestSupport;
import cl.duoc.pedidos360.notificaciones.messaging.support.ReintentoHandler;
import cl.duoc.pedidos360.notificaciones.service.DestinatarioInvalidoException;
import cl.duoc.pedidos360.notificaciones.service.NotificacionService;
import com.rabbitmq.client.Channel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;

import static cl.duoc.pedidos360.notificaciones.messaging.consumer.ListenerTestSupport.TAG;
import static cl.duoc.pedidos360.notificaciones.messaging.consumer.ListenerTestSupport.lector;
import static cl.duoc.pedidos360.notificaciones.messaging.consumer.ListenerTestSupport.mensaje;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class PedidoCreadoListenerTest {

    private static final String JSON = """
            {"pedidoId":12,"clienteNombre":"Ana","clienteEmail":"ana@duoc.cl","total":11900.0}""";

    private final RabbitProperties props = ListenerTestSupport.props();
    private NotificacionService service;
    private ReintentoHandler reintentos;
    private Channel canal;
    private PedidoCreadoListener listener;

    @BeforeEach
    void preparar() {
        service = mock(NotificacionService.class);
        reintentos = mock(ReintentoHandler.class);
        canal = mock(Channel.class);
        listener = new PedidoCreadoListener(service, lector(), reintentos, props);
    }

    @Test
    void notificacionEnviada_haceAck() throws Exception {
        listener.onPedidoCreado(mensaje(JSON), canal);

        verify(service).notificarPedidoRecibido(12L, "Ana", "ana@duoc.cl", 11900.0);
        verify(canal).basicAck(TAG, false);
    }

    @Test
    void sinPedidoId_vaDirectoADlq() throws Exception {
        listener.onPedidoCreado(mensaje("{\"clienteEmail\":\"ana@duoc.cl\"}"), canal);

        verify(canal).basicNack(TAG, false, false);
        verifyNoInteractions(service, reintentos);
    }

    @Test
    void emailInvalido_noSeReintentaYVaADlq() throws Exception {
        doThrow(new DestinatarioInvalidoException("sin email"))
                .when(service).notificarPedidoRecibido(any(), any(), any(), any());

        listener.onPedidoCreado(mensaje(JSON), canal);

        verify(canal).basicNack(TAG, false, false);
        verifyNoInteractions(reintentos);
    }

    @Test
    void servidorDeCorreoCaido_seReintenta() throws Exception {
        doThrow(new IllegalStateException("SMTP no responde"))
                .when(service).notificarPedidoRecibido(any(), any(), any(), any());
        Message mensaje = mensaje(JSON);

        listener.onPedidoCreado(mensaje, canal);

        verify(reintentos).reintentarOEnviarADlq(eq(mensaje), eq(canal), eq(props.colas().pedidoCreado()), any());
        verify(canal, never()).basicAck(anyLong(), anyBoolean());
        verify(canal, never()).basicNack(anyLong(), anyBoolean(), anyBoolean());
    }
}
