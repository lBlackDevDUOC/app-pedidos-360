package cl.duoc.pedidos360.documentos.messaging.consumer.pedidos;

import cl.duoc.pedidos360.documentos.messaging.config.RabbitProperties;
import cl.duoc.pedidos360.documentos.messaging.producer.DocumentoEventPublisher;
import cl.duoc.pedidos360.documentos.messaging.producer.PublicacionFallidaException;
import cl.duoc.pedidos360.documentos.messaging.support.MensajeLector;
import cl.duoc.pedidos360.documentos.messaging.support.ReintentoHandler;
import cl.duoc.pedidos360.documentos.model.Comprobante;
import cl.duoc.pedidos360.documentos.service.ComprobanteService;
import cl.duoc.pedidos360.documentos.service.DatosPedidoInvalidosException;
import cl.duoc.pedidos360.documentos.service.ServicioNoDisponibleException;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.rabbitmq.client.Channel;
import jakarta.validation.Validation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;

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
import static org.mockito.Mockito.when;

/** Rúbrica: cada camino del consumidor termina en una decisión explícita de ACK, NACK o reintento. */
class PedidoCreadoListenerTest {

    private static final long TAG = 5L;
    private static final String JSON_VALIDO = """
            {"eventoId":"e-1","pedidoId":12,"clienteNombre":"Ana","clienteEmail":"ana@duoc.cl",
             "total":11900.0,"estado":"PENDIENTE"}""";
    private static final Comprobante COMPROBANTE = new Comprobante("BOL-00000012", 12L, "Ana", "ana@duoc.cl",
            11900, 10000, 1900, LocalDateTime.now());

    private final RabbitProperties props = new RabbitProperties(
            new RabbitProperties.Exchanges("t.events", "t.retry", "t.dlx"),
            new RabbitProperties.RoutingKeys("pedido.creado", "documento.generado"),
            new RabbitProperties.Colas(new RabbitProperties.Cola("t.q", "t.retry.q", "t.dlq")),
            new RabbitProperties.Reintentos(3, 5000),
            new RabbitProperties.Retencion(600000, 1000, 86400000, 5000));

    private ComprobanteService comprobanteService;
    private DocumentoEventPublisher publisher;
    private ReintentoHandler reintentos;
    private Channel canal;
    private PedidoCreadoListener listener;

    @BeforeEach
    void preparar() {
        comprobanteService = mock(ComprobanteService.class);
        publisher = mock(DocumentoEventPublisher.class);
        reintentos = mock(ReintentoHandler.class);
        canal = mock(Channel.class);
        MensajeLector lector = new MensajeLector(
                JsonMapper.builder().findAndAddModules().build(),
                Validation.buildDefaultValidatorFactory().getValidator());
        listener = new PedidoCreadoListener(comprobanteService, publisher, lector, reintentos, props);
    }

    @Test
    void pedidoValido_emiteComprobantePublicaYHaceAck() throws Exception {
        when(comprobanteService.emitir(12L, "Ana", "ana@duoc.cl", 11900.0)).thenReturn(COMPROBANTE);

        listener.onPedidoCreado(mensaje(JSON_VALIDO), canal);

        verify(publisher).publicarDocumentoGenerado(COMPROBANTE);
        verify(canal).basicAck(TAG, false);
        verify(canal, never()).basicNack(anyLong(), anyBoolean(), anyBoolean());
    }

    @Test
    void jsonCorrupto_vaDirectoADlq() throws Exception {
        listener.onPedidoCreado(mensaje("no-es-json"), canal);

        verify(canal).basicNack(TAG, false, false);
        verifyNoInteractions(comprobanteService, publisher, reintentos);
    }

    @Test
    void datosInvalidos_noSeReintentanYVanADlq() throws Exception {
        when(comprobanteService.emitir(any(), any(), any(), any()))
                .thenThrow(new DatosPedidoInvalidosException("total inválido"));

        listener.onPedidoCreado(mensaje(JSON_VALIDO), canal);

        verify(canal).basicNack(TAG, false, false);
        verifyNoInteractions(publisher, reintentos);
    }

    @Test
    void servicioExternoCaido_seReintenta() throws Exception {
        when(comprobanteService.emitir(any(), any(), any(), any()))
                .thenThrow(new ServicioNoDisponibleException("timeout"));
        Message mensaje = mensaje(JSON_VALIDO);

        listener.onPedidoCreado(mensaje, canal);

        verify(reintentos).reintentarOEnviarADlq(eq(mensaje), eq(canal), eq(props.colas().pedidoCreado()), any());
        verify(canal, never()).basicAck(anyLong(), anyBoolean());
    }

    @Test
    void falloAlPublicarDocumentoGenerado_seReintentaSinHacerAck() throws Exception {
        when(comprobanteService.emitir(any(), any(), any(), any())).thenReturn(COMPROBANTE);
        doThrow(new PublicacionFallidaException("broker caído", null))
                .when(publisher).publicarDocumentoGenerado(COMPROBANTE);
        Message mensaje = mensaje(JSON_VALIDO);

        listener.onPedidoCreado(mensaje, canal);

        verify(reintentos).reintentarOEnviarADlq(eq(mensaje), eq(canal), eq(props.colas().pedidoCreado()), any());
        verify(canal, never()).basicAck(anyLong(), anyBoolean());
    }

    private static Message mensaje(String json) {
        MessageProperties propiedades = new MessageProperties();
        propiedades.setDeliveryTag(TAG);
        return new Message(json.getBytes(StandardCharsets.UTF_8), propiedades);
    }
}
