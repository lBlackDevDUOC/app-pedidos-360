package cl.duoc.pedidos360.notificaciones.messaging.consumer.documentos;

import cl.duoc.pedidos360.notificaciones.messaging.config.RabbitProperties;
import cl.duoc.pedidos360.notificaciones.messaging.consumer.ListenerTestSupport;
import cl.duoc.pedidos360.notificaciones.messaging.support.ReintentoHandler;
import cl.duoc.pedidos360.notificaciones.service.NotificacionService;
import com.rabbitmq.client.Channel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;

import static cl.duoc.pedidos360.notificaciones.messaging.consumer.ListenerTestSupport.TAG;
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

class DocumentoGeneradoListenerTest {

    private static final String JSON = """
            {"pedidoId":12,"numeroComprobante":"BOL-00000012","clienteNombre":"Ana","clienteEmail":"ana@duoc.cl"}""";

    private final RabbitProperties props = ListenerTestSupport.props();
    private NotificacionService service;
    private ReintentoHandler reintentos;
    private Channel canal;
    private DocumentoGeneradoListener listener;

    @BeforeEach
    void preparar() {
        service = mock(NotificacionService.class);
        reintentos = mock(ReintentoHandler.class);
        canal = mock(Channel.class);
        listener = new DocumentoGeneradoListener(service, ListenerTestSupport.lector(), reintentos, props);
    }

    @Test
    void notificacionEnviada_haceAck() throws Exception {
        listener.onDocumentoGenerado(mensaje(JSON), canal);

        verify(service).notificarComprobanteEmitido(12L, "BOL-00000012", "Ana", "ana@duoc.cl");
        verify(canal).basicAck(TAG, false);
    }

    @Test
    void sinNumeroDeComprobante_vaDirectoADlq() throws Exception {
        listener.onDocumentoGenerado(mensaje("{\"pedidoId\":12}"), canal);

        verify(canal).basicNack(TAG, false, false);
        verifyNoInteractions(service, reintentos);
    }

    @Test
    void falloTransitorio_seReintenta() throws Exception {
        doThrow(new IllegalStateException("SMTP no responde"))
                .when(service).notificarComprobanteEmitido(any(), any(), any(), any());
        Message mensaje = mensaje(JSON);

        listener.onDocumentoGenerado(mensaje, canal);

        verify(reintentos).reintentarOEnviarADlq(eq(mensaje), eq(canal), eq(props.colas().documentoGenerado()), any());
        verify(canal, never()).basicAck(anyLong(), anyBoolean());
    }
}
