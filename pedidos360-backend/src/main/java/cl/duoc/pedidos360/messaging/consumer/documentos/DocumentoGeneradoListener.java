package cl.duoc.pedidos360.messaging.consumer.documentos;

import cl.duoc.pedidos360.messaging.config.RabbitProperties;
import cl.duoc.pedidos360.messaging.event.DocumentoGeneradoEvent;
import cl.duoc.pedidos360.messaging.support.MensajeInvalidoException;
import cl.duoc.pedidos360.messaging.support.MensajeLector;
import cl.duoc.pedidos360.messaging.support.ReintentoHandler;
import cl.duoc.pedidos360.service.PedidoNoEncontradoException;
import cl.duoc.pedidos360.service.PedidoService;
import com.rabbitmq.client.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Consumidor del dominio "documentos": cuando ms-documentos emite un comprobante,
 * se guarda su número en el pedido.
 *
 * <p>Manejo de confirmación (ACK manual):</p>
 * <ul>
 *   <li>Procesado OK → {@code basicAck}: RabbitMQ elimina el mensaje.</li>
 *   <li>Mensaje inválido o pedido inexistente (no recuperable) → {@code basicNack(requeue=false)}: directo a la DLQ.</li>
 *   <li>Cualquier otro error (recuperable, ej. BD no disponible) → reintento diferido; agotados → DLQ.</li>
 * </ul>
 */
@Component
public class DocumentoGeneradoListener {

    private static final Logger log = LoggerFactory.getLogger(DocumentoGeneradoListener.class);

    private final PedidoService pedidoService;
    private final MensajeLector lector;
    private final ReintentoHandler reintentos;
    private final RabbitProperties props;

    public DocumentoGeneradoListener(PedidoService pedidoService, MensajeLector lector,
                                     ReintentoHandler reintentos, RabbitProperties props) {
        this.pedidoService = pedidoService;
        this.lector = lector;
        this.reintentos = reintentos;
        this.props = props;
    }

    @RabbitListener(queues = "${app.rabbitmq.colas.documento-generado.nombre}")
    public void onDocumentoGenerado(Message mensaje, Channel canal) throws IOException {
        long deliveryTag = mensaje.getMessageProperties().getDeliveryTag();
        try {
            DocumentoGeneradoEvent evento = lector.leer(mensaje, DocumentoGeneradoEvent.class);
            pedidoService.registrarComprobante(evento.pedidoId(), evento.numeroComprobante(), evento.fechaEmision());
            canal.basicAck(deliveryTag, false);
            log.info("[ACK] Comprobante {} registrado en pedido {}", evento.numeroComprobante(), evento.pedidoId());
        } catch (MensajeInvalidoException | PedidoNoEncontradoException e) {
            log.error("[NACK->DLQ] {} no recuperable: {}", MensajeLector.resumen(mensaje), e.getMessage());
            canal.basicNack(deliveryTag, false, false);
        } catch (RuntimeException e) {
            reintentos.reintentarOEnviarADlq(mensaje, canal, props.colas().documentoGenerado(), e);
        }
    }
}
