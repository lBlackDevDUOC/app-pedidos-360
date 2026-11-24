package cl.duoc.pedidos360.notificaciones.messaging.consumer.pedidos;

import cl.duoc.pedidos360.notificaciones.messaging.config.RabbitProperties;
import cl.duoc.pedidos360.notificaciones.messaging.event.PedidoCreadoEvent;
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
 * Consumidor del dominio "pedidos": envía el email "recibimos tu pedido".
 *
 * <ul>
 *   <li>Email enviado → {@code basicAck}.</li>
 *   <li>Mensaje inválido o cliente sin email válido (no recuperable) → {@code basicNack(requeue=false)} → DLQ.</li>
 *   <li>Fallo del envío (recuperable, ej. servidor de correo caído) → reintento diferido; agotados → DLQ.</li>
 * </ul>
 */
@Component
public class PedidoCreadoListener {

    private static final Logger log = LoggerFactory.getLogger(PedidoCreadoListener.class);

    private final NotificacionService notificacionService;
    private final MensajeLector lector;
    private final ReintentoHandler reintentos;
    private final RabbitProperties props;

    public PedidoCreadoListener(NotificacionService notificacionService, MensajeLector lector,
                                ReintentoHandler reintentos, RabbitProperties props) {
        this.notificacionService = notificacionService;
        this.lector = lector;
        this.reintentos = reintentos;
        this.props = props;
    }

    @RabbitListener(queues = "${app.rabbitmq.colas.pedido-creado.nombre}")
    public void onPedidoCreado(Message mensaje, Channel canal) throws IOException {
        long deliveryTag = mensaje.getMessageProperties().getDeliveryTag();
        try {
            PedidoCreadoEvent evento = lector.leer(mensaje, PedidoCreadoEvent.class);
            notificacionService.notificarPedidoRecibido(
                    evento.pedidoId(), evento.clienteNombre(), evento.clienteEmail(), evento.total());
            canal.basicAck(deliveryTag, false);
            log.info("[ACK] Notificación de pedido recibido enviada (pedido {})", evento.pedidoId());
        } catch (MensajeInvalidoException | DestinatarioInvalidoException e) {
            log.error("[NACK->DLQ] {} no recuperable: {}", MensajeLector.resumen(mensaje), e.getMessage());
            canal.basicNack(deliveryTag, false, false);
        } catch (RuntimeException e) {
            reintentos.reintentarOEnviarADlq(mensaje, canal, props.colas().pedidoCreado(), e);
        }
    }
}
