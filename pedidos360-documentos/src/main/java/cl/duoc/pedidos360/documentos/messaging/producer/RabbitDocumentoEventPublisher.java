package cl.duoc.pedidos360.documentos.messaging.producer;

import cl.duoc.pedidos360.documentos.messaging.config.RabbitProperties;
import cl.duoc.pedidos360.documentos.messaging.event.DocumentoGeneradoEvent;
import cl.duoc.pedidos360.documentos.model.Comprobante;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;

/** Productor: publica documento.generado en el exchange de eventos. */
@Component
public class RabbitDocumentoEventPublisher implements DocumentoEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(RabbitDocumentoEventPublisher.class);

    private final RabbitTemplate rabbitTemplate;
    private final RabbitProperties props;

    public RabbitDocumentoEventPublisher(RabbitTemplate rabbitTemplate, RabbitProperties props) {
        this.rabbitTemplate = rabbitTemplate;
        this.props = props;
    }

    @Override
    public void publicarDocumentoGenerado(Comprobante comprobante) {
        DocumentoGeneradoEvent evento = new DocumentoGeneradoEvent(
                UUID.randomUUID().toString(),
                comprobante.pedidoId(),
                comprobante.numero(),
                comprobante.clienteEmail(),
                comprobante.clienteNombre(),
                comprobante.total(),
                comprobante.neto(),
                comprobante.iva(),
                comprobante.fechaEmision());

        String routingKey = props.routingKeys().documentoGenerado();
        try {
            rabbitTemplate.convertAndSend(props.exchanges().eventos(), routingKey, evento, mensaje -> {
                mensaje.getMessageProperties().setMessageId(evento.eventoId());
                mensaje.getMessageProperties().setDeliveryMode(MessageDeliveryMode.PERSISTENT);
                return mensaje;
            }, new CorrelationData(evento.eventoId()));
            log.info("[PUBLICADO] {} comprobante={} pedidoId={}", routingKey, comprobante.numero(), comprobante.pedidoId());
        } catch (AmqpException e) {
            throw new PublicacionFallidaException("No se pudo publicar " + routingKey + " del pedido "
                    + comprobante.pedidoId(), e);
        }
    }
}
