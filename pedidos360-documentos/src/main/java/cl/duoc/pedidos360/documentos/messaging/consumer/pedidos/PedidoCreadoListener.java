package cl.duoc.pedidos360.documentos.messaging.consumer.pedidos;

import cl.duoc.pedidos360.documentos.messaging.config.RabbitProperties;
import cl.duoc.pedidos360.documentos.messaging.event.PedidoCreadoEvent;
import cl.duoc.pedidos360.documentos.messaging.producer.DocumentoEventPublisher;
import cl.duoc.pedidos360.documentos.messaging.support.MensajeInvalidoException;
import cl.duoc.pedidos360.documentos.messaging.support.MensajeLector;
import cl.duoc.pedidos360.documentos.messaging.support.ReintentoHandler;
import cl.duoc.pedidos360.documentos.model.Comprobante;
import cl.duoc.pedidos360.documentos.service.ComprobanteService;
import cl.duoc.pedidos360.documentos.service.DatosPedidoInvalidosException;
import com.rabbitmq.client.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Consumidor del dominio "pedidos": por cada pedido creado emite el comprobante y publica documento.generado.
 *
 * <p>Manejo de confirmación (ACK manual):</p>
 * <ul>
 *   <li>Comprobante emitido y evento publicado → {@code basicAck}.</li>
 *   <li>Mensaje inválido o datos del pedido inválidos (no recuperable) → {@code basicNack(requeue=false)} → DLQ.</li>
 *   <li>Servicio externo caído o broker sin conexión (recuperable) → reintento diferido; agotados → DLQ.</li>
 * </ul>
 * El ACK va después de publicar: si el servicio cae entre medio, el mensaje se reentrega y como la
 * emisión es idempotente el resultado es el mismo comprobante.
 */
@Component
public class PedidoCreadoListener {

    private static final Logger log = LoggerFactory.getLogger(PedidoCreadoListener.class);

    private final ComprobanteService comprobanteService;
    private final DocumentoEventPublisher publisher;
    private final MensajeLector lector;
    private final ReintentoHandler reintentos;
    private final RabbitProperties props;

    public PedidoCreadoListener(ComprobanteService comprobanteService, DocumentoEventPublisher publisher,
                                MensajeLector lector, ReintentoHandler reintentos, RabbitProperties props) {
        this.comprobanteService = comprobanteService;
        this.publisher = publisher;
        this.lector = lector;
        this.reintentos = reintentos;
        this.props = props;
    }

    @RabbitListener(queues = "${app.rabbitmq.colas.pedido-creado.nombre}")
    public void onPedidoCreado(Message mensaje, Channel canal) throws IOException {
        long deliveryTag = mensaje.getMessageProperties().getDeliveryTag();
        try {
            PedidoCreadoEvent evento = lector.leer(mensaje, PedidoCreadoEvent.class);
            Comprobante comprobante = comprobanteService.emitir(
                    evento.pedidoId(), evento.clienteNombre(), evento.clienteEmail(), evento.total());
            publisher.publicarDocumentoGenerado(comprobante);
            canal.basicAck(deliveryTag, false);
            log.info("[ACK] Pedido {} procesado, comprobante {}", evento.pedidoId(), comprobante.numero());
        } catch (MensajeInvalidoException | DatosPedidoInvalidosException e) {
            log.error("[NACK->DLQ] {} no recuperable: {}", MensajeLector.resumen(mensaje), e.getMessage());
            canal.basicNack(deliveryTag, false, false);
        } catch (RuntimeException e) {
            reintentos.reintentarOEnviarADlq(mensaje, canal, props.colas().pedidoCreado(), e);
        }
    }
}
