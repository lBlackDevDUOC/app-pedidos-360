package cl.duoc.pedidos360.notificaciones.messaging.consumer.documentos;

import cl.duoc.pedidos360.notificaciones.messaging.config.RabbitProperties;
import cl.duoc.pedidos360.notificaciones.messaging.event.DocumentoGeneradoEvent;
import cl.duoc.pedidos360.notificaciones.messaging.support.MensajeInvalidoException;
import cl.duoc.pedidos360.notificaciones.messaging.support.MensajeLector;
import cl.duoc.pedidos360.notificaciones.messaging.support.ReintentoHandler;
import cl.duoc.pedidos360.notificaciones.service.DestinatarioInvalidoException;
import cl.duoc.pedidos360.notificaciones.service.NotificacionService;
import com.rabbitmq.client.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Consumidor del dominio "documentos": envía el email "tu comprobante está listo".
 *
 * <ul>
 *   <li>Email enviado → {@code basicAck}.</li>
 *   <li>Mensaje inválido o cliente sin email válido (no recuperable) → {@code basicNack(requeue=false)} → DLQ.</li>
 *   <li>Fallo del envío (recuperable) → reintento diferido; agotados → DLQ.</li>
 * </ul>
 */
@Component
public class DocumentoGeneradoListener {

    private static final Logger log = LoggerFactory.getLogger(DocumentoGeneradoListener.class);

    private final NotificacionService notificacionService;
    private final MensajeLector lector;
    private final ReintentoHandler reintentos;
    private final RabbitProperties props;

    public DocumentoGeneradoListener(NotificacionService notificacionService, MensajeLector lector,
                                     ReintentoHandler reintentos, RabbitProperties props) {
        this.notificacionService = notificacionService;
        this.lector = lector;
        this.reintentos = reintentos;
        this.props = props;
    }

    @RabbitListener(queues = "${app.rabbitmq.colas.documento-generado.nombre}")
    public void onDocumentoGenerado(Message mensaje, Channel canal) throws IOException {
        long deliveryTag = mensaje.getMessageProperties().getDeliveryTag();
        try {
            DocumentoGeneradoEvent evento = lector.leer(mensaje, DocumentoGeneradoEvent.class);
            notificacionService.notificarComprobanteEmitido(
                    evento.pedidoId(), evento.numeroComprobante(), evento.clienteNombre(), evento.clienteEmail());
            canal.basicAck(deliveryTag, false);
            log.info("[ACK] Notificación de comprobante {} enviada (pedido {})",
                    evento.numeroComprobante(), evento.pedidoId());
        } catch (MensajeInvalidoException | DestinatarioInvalidoException e) {
            log.error("[NACK->DLQ] {} no recuperable: {}", MensajeLector.resumen(mensaje), e.getMessage());
            canal.basicNack(deliveryTag, false, false);
        } catch (RuntimeException e) {
            reintentos.reintentarOEnviarADlq(mensaje, canal, props.colas().documentoGenerado(), e);
        }
    }
}
