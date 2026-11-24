package cl.duoc.pedidos360.documentos.messaging.config;

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
 * Topología RabbitMQ de ms-documentos (declarada por RabbitAdmin al conectar, de forma idempotente).
 *
 * <h2>Rutas de mensajería</h2>
 * <pre>
 * CONSUME  pedido.creado      → [pedidos360.events] → documentos.pedido-creado.q
 *              error recuperable → [pedidos360.retry] → .retry (espera TTL) → vuelve a la cola
 *              error definitivo  → [pedidos360.dlx]   → .dlq (cuarentena)
 * PUBLICA  documento.generado → [pedidos360.events] → colas de ms-pedidos y ms-notificaciones
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

    /** Exchange de eventos de negocio (topic). Durable. */
    @Bean
    public TopicExchange eventosExchange() {
        return ExchangeBuilder.topicExchange(props.exchanges().eventos()).durable(true).build();
    }

    /** Exchange de reintentos (direct): la routing key es el nombre de la cola a reintentar. */
    @Bean
    public DirectExchange reintentosExchange() {
        return ExchangeBuilder.directExchange(props.exchanges().reintentos()).durable(true).build();
    }

    /** Dead Letter Exchange (direct): recibe los mensajes rechazados, expirados o desbordados. */
    @Bean
    public DirectExchange deadLetterExchange() {
        return ExchangeBuilder.directExchange(props.exchanges().deadLetter()).durable(true).build();
    }

    // ------------------------------------------------------------------ Caso de uso: pedido.creado → emitir comprobante

    /**
     * Cola de trabajo: pedidos recién creados que necesitan comprobante.
     * Retención: TTL por mensaje y largo máximo; lo que expira o desborda va al DLX.
     */
    @Bean
    public Queue pedidoCreadoQueue() {
        RabbitProperties.Cola cola = props.colas().pedidoCreado();
        return QueueBuilder.durable(cola.nombre())
                .deadLetterExchange(props.exchanges().deadLetter())
                .deadLetterRoutingKey(cola.nombre())
                .ttl(props.retencion().ttlColaMs())
                .maxLength(props.retencion().maxMensajesCola())
                .overflow(QueueBuilder.Overflow.dropHead)
                .build();
    }

    @Bean
    public Binding pedidoCreadoBinding() {
        return BindingBuilder.bind(pedidoCreadoQueue())
                .to(eventosExchange())
                .with(props.routingKeys().pedidoCreado());
    }

    /** Cola de reintento: sin consumidores; al vencer el TTL el mensaje vuelve a la cola de trabajo. */
    @Bean
    public Queue pedidoCreadoRetryQueue() {
        RabbitProperties.Cola cola = props.colas().pedidoCreado();
        return QueueBuilder.durable(cola.reintento())
                .ttl(props.reintentos().esperaMs())
                .deadLetterExchange("")
                .deadLetterRoutingKey(cola.nombre())
                .build();
    }

    @Bean
    public Binding pedidoCreadoRetryBinding() {
        return BindingBuilder.bind(pedidoCreadoRetryQueue())
                .to(reintentosExchange())
                .with(props.colas().pedidoCreado().nombre());
    }

    /** DLQ: cuarentena acotada por TTL y largo máximo. */
    @Bean
    public Queue pedidoCreadoDlq() {
        RabbitProperties.Cola cola = props.colas().pedidoCreado();
        return QueueBuilder.durable(cola.dlq())
                .ttl(props.retencion().ttlDlqMs())
                .maxLength(props.retencion().maxMensajesDlq())
                .build();
    }

    @Bean
    public Binding pedidoCreadoDlqBinding() {
        return BindingBuilder.bind(pedidoCreadoDlq())
                .to(deadLetterExchange())
                .with(props.colas().pedidoCreado().nombre());
    }
}
