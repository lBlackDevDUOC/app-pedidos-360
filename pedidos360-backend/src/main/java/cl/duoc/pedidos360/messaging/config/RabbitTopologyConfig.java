package cl.duoc.pedidos360.messaging.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.ExchangeBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Topología RabbitMQ de ms-pedidos. Spring (RabbitAdmin) declara estos beans en el broker al conectar;
 * declarar un recurso que ya existe con los mismos argumentos no tiene efecto (es idempotente).
 *
 * <h2>Rutas de mensajería</h2>
 * <pre>
 * PUBLICA  pedido.creado      → [pedidos360.events] → colas de ms-documentos y ms-notificaciones
 * CONSUME  documento.generado → [pedidos360.events] → pedidos.documento-generado.q
 *              error recuperable → [pedidos360.retry] → .retry (espera TTL) → vuelve a la cola
 *              error definitivo  → [pedidos360.dlx]   → .dlq (cuarentena)
 * </pre>
 * Los nombres salen de {@link RabbitProperties} (application.yml).
 */
@Configuration
public class RabbitTopologyConfig {

    private final RabbitProperties props;

    public RabbitTopologyConfig(RabbitProperties props) {
        this.props = props;
    }

    // ------------------------------------------------------------------ Exchanges

    /** Exchange de eventos de negocio (topic): cada servicio enlaza sus colas por routing key. Durable. */
    @Bean
    public TopicExchange eventosExchange() {
        return ExchangeBuilder.topicExchange(props.exchanges().eventos()).durable(true).build();
    }

    /** Exchange de reintentos (direct): la routing key es el nombre de la cola que debe reintentarse. */
    @Bean
    public DirectExchange reintentosExchange() {
        return ExchangeBuilder.directExchange(props.exchanges().reintentos()).durable(true).build();
    }

    /** Dead Letter Exchange (direct): recibe los mensajes rechazados, expirados o desbordados. */
    @Bean
    public DirectExchange deadLetterExchange() {
        return ExchangeBuilder.directExchange(props.exchanges().deadLetter()).durable(true).build();
    }

    // ------------------------------------------------------------------ Caso de uso: documento.generado

    /**
     * Cola de trabajo: ms-documentos avisa que emitió el comprobante de un pedido.
     * Retención: los mensajes expiran tras ttl-cola-ms y la cola admite como máximo max-mensajes-cola
     * (al desbordar se descartan los más antiguos). En ambos casos el mensaje va al DLX, no se pierde.
     */
    @Bean
    public Queue documentoGeneradoQueue() {
        RabbitProperties.Cola cola = props.colas().documentoGenerado();
        return QueueBuilder.durable(cola.nombre())
                .deadLetterExchange(props.exchanges().deadLetter())
                .deadLetterRoutingKey(cola.nombre())
                .ttl(props.retencion().ttlColaMs())
                .maxLength(props.retencion().maxMensajesCola())
                .overflow(QueueBuilder.Overflow.dropHead)
                .build();
    }

    /** Enlaza la cola de trabajo con el evento documento.generado. */
    @Bean
    public Binding documentoGeneradoBinding() {
        return BindingBuilder.bind(documentoGeneradoQueue())
                .to(eventosExchange())
                .with(props.routingKeys().documentoGenerado());
    }

    /**
     * Cola de reintento: no tiene consumidores. El mensaje espera esperaMs (TTL) y al expirar
     * RabbitMQ lo devuelve a la cola de trabajo por el exchange por defecto ("").
     */
    @Bean
    public Queue documentoGeneradoRetryQueue() {
        RabbitProperties.Cola cola = props.colas().documentoGenerado();
        return QueueBuilder.durable(cola.reintento())
                .ttl(props.reintentos().esperaMs())
                .deadLetterExchange("")
                .deadLetterRoutingKey(cola.nombre())
                .build();
    }

    @Bean
    public Binding documentoGeneradoRetryBinding() {
        return BindingBuilder.bind(documentoGeneradoRetryQueue())
                .to(reintentosExchange())
                .with(props.colas().documentoGenerado().nombre());
    }

    /** DLQ: cuarentena de mensajes que no se pudieron procesar, acotada por TTL y largo máximo. */
    @Bean
    public Queue documentoGeneradoDlq() {
        RabbitProperties.Cola cola = props.colas().documentoGenerado();
        return QueueBuilder.durable(cola.dlq())
                .ttl(props.retencion().ttlDlqMs())
                .maxLength(props.retencion().maxMensajesDlq())
                .build();
    }

    @Bean
    public Binding documentoGeneradoDlqBinding() {
        return BindingBuilder.bind(documentoGeneradoDlq())
                .to(deadLetterExchange())
                .with(props.colas().documentoGenerado().nombre());
    }
}
