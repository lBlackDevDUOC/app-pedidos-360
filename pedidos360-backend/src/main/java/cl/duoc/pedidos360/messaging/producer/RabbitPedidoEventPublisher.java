package cl.duoc.pedidos360.messaging.producer;

import cl.duoc.pedidos360.messaging.config.RabbitProperties;
import cl.duoc.pedidos360.messaging.event.PedidoCreadoEvent;
import cl.duoc.pedidos360.model.Pedido;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;

/** Productor: publica los eventos de pedidos en el exchange de eventos de RabbitMQ. */
@Component
public class RabbitPedidoEventPublisher implements PedidoEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(RabbitPedidoEventPublisher.class);

    private final RabbitTemplate rabbitTemplate;
    private final RabbitProperties props;

    public RabbitPedidoEventPublisher(RabbitTemplate rabbitTemplate, RabbitProperties props) {
        this.rabbitTemplate = rabbitTemplate;
        this.props = props;
    }

    @Override
    public void publicarPedidoCreado(Pedido pedido) {
        PedidoCreadoEvent evento = new PedidoCreadoEvent(
                UUID.randomUUID().toString(),
                pedido.getId(),
                pedido.getCliente().getNombre(),
                pedido.getCliente().getEmail(),
                pedido.getTotal(),
                pedido.getEstado(),
                pedido.getFechaCreacion());

        String exchange = props.exchanges().eventos();
        String routingKey = props.routingKeys().pedidoCreado();
        try {
            rabbitTemplate.convertAndSend(exchange, routingKey, evento, mensaje -> {
                mensaje.getMessageProperties().setMessageId(evento.eventoId());
                // Persistente: sobrevive a un reinicio de RabbitMQ (la cola también es durable)
                mensaje.getMessageProperties().setDeliveryMode(MessageDeliveryMode.PERSISTENT);
                return mensaje;
            }, new CorrelationData(evento.eventoId()));
            log.info("[PUBLICADO] {} pedidoId={} eventoId={}", routingKey, pedido.getId(), evento.eventoId());
        } catch (AmqpException e) {
            // El pedido ya quedó guardado en la BD: la caída del broker no debe romper la creación de pedidos
            log.error("[PUBLICACION-FALLIDA] No se pudo publicar {} del pedido {}: {}",
                    routingKey, pedido.getId(), e.getMessage());
        }
    }
}
