package cl.duoc.pedidos360.messaging.consumer.documentos;

import cl.duoc.pedidos360.messaging.PropiedadesDePrueba;
import cl.duoc.pedidos360.messaging.config.RabbitProperties;
import cl.duoc.pedidos360.messaging.support.MensajeLector;
import cl.duoc.pedidos360.messaging.support.ReintentoHandler;
import cl.duoc.pedidos360.service.PedidoNoEncontradoException;
import cl.duoc.pedidos360.service.PedidoService;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.rabbitmq.client.Channel;
import jakarta.validation.Validation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.dao.DataAccessResourceFailureException;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/** Rúbrica: cada camino del consumidor termina en una decisión explícita de ACK, NACK o reintento. */
class DocumentoGeneradoListenerTest {

    private static final long TAG = 42L;
    private static final String JSON_VALIDO = """
            {"eventoId":"e-1","pedidoId":7,"numeroComprobante":"BOL-00000007",
             "fechaEmision":"2026-10-18T10:00:00"}""";

    private final RabbitProperties props = PropiedadesDePrueba.crear();
    private PedidoService pedidoService;
    private ReintentoHandler reintentos;
    private Channel canal;
    private DocumentoGeneradoListener listener;

    @BeforeEach
    void preparar() {
        pedidoService = mock(PedidoService.class);
        reintentos = mock(ReintentoHandler.class);
        canal = mock(Channel.class);
        MensajeLector lector = new MensajeLector(
                JsonMapper.builder().findAndAddModules().build(),
                Validation.buildDefaultValidatorFactory().getValidator());
        listener = new DocumentoGeneradoListener(pedidoService, lector, reintentos, props);
    }

    @Test
    void mensajeValido_registraComprobanteYHaceAck() throws Exception {
        listener.onDocumentoGenerado(mensaje(JSON_VALIDO), canal);

        verify(pedidoService).registrarComprobante(7L, "BOL-00000007", LocalDateTime.of(2026, 10, 18, 10, 0));
        verify(canal).basicAck(TAG, false);
        verify(canal, never()).basicNack(anyLong(), anyBoolean(), anyBoolean());
    }

    @Test
    void jsonCorrupto_vaDirectoADlqSinReintentar() throws Exception {
        listener.onDocumentoGenerado(mensaje("{esto no es json"), canal);

        verify(canal).basicNack(TAG, false, false);
        verifyNoInteractions(pedidoService, reintentos);
    }

    @Test
    void faltanCamposObligatorios_vaDirectoADlq() throws Exception {
        listener.onDocumentoGenerado(mensaje("{\"eventoId\":\"e-1\"}"), canal);

        verify(canal).basicNack(TAG, false, false);
        verifyNoInteractions(pedidoService, reintentos);
    }

    @Test
    void pedidoInexistente_esNoRecuperableYVaADlq() throws Exception {
        doThrow(new PedidoNoEncontradoException(7L))
                .when(pedidoService).registrarComprobante(eq(7L), any(), any());

        listener.onDocumentoGenerado(mensaje(JSON_VALIDO), canal);

        verify(canal).basicNack(TAG, false, false);
        verifyNoInteractions(reintentos);
    }

    @Test
    void baseDeDatosCaida_esRecuperableYSeReintenta() throws Exception {
        doThrow(new DataAccessResourceFailureException("BD no disponible"))
                .when(pedidoService).registrarComprobante(eq(7L), any(), any());
        Message mensaje = mensaje(JSON_VALIDO);

        listener.onDocumentoGenerado(mensaje, canal);

        verify(reintentos).reintentarOEnviarADlq(eq(mensaje), eq(canal), eq(props.colas().documentoGenerado()), any());
        verify(canal, never()).basicAck(anyLong(), anyBoolean());
        verify(canal, never()).basicNack(anyLong(), anyBoolean(), anyBoolean());
    }

    private static Message mensaje(String json) {
        MessageProperties propiedades = new MessageProperties();
        propiedades.setDeliveryTag(TAG);
        return new Message(json.getBytes(StandardCharsets.UTF_8), propiedades);
    }
}
