package cl.duoc.pedidos360.notificaciones.messaging.config;

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
 * Topología RabbitMQ de ms-notificaciones (declarada por RabbitAdmin al conectar, de forma idempotente).
 *
 * <h2>Rutas de mensajería</h2>
 * <pre>
 * CONSUME  pedido.creado      → [pedidos360.events] → notificaciones.pedido-creado.q      (email "pedido recibido")
 * CONSUME  documento.generado → [pedidos360.events] → notificaciones.documento-generado.q (email "comprobante listo")
 *   En ambas: error recuperable → [pedidos360.retry] → .retry → vuelve a la cola
 *             error definitivo  → [pedidos360.dlx]   → .dlq
 * </pre>
 * Cada cola recibe su propia copia del evento (patrón publish/subscribe): ms-notificaciones y ms-documentos
 * procesan el mismo pedido.creado de forma independiente.
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

    // ------------------------------------------------------------------ Caso de uso: pedido.creado → email de confirmación

    /** Cola de trabajo con retención (TTL + largo máximo) y DLX. */
    @Bean
    public Queue pedidoCreadoQueue() {
        return colaDeTrabajo(props.colas().pedidoCreado());
    }

    @Bean
    public Binding pedidoCreadoBinding() {
        return BindingBuilder.bind(pedidoCreadoQueue()).to(eventosExchange()).with(props.routingKeys().pedidoCreado());
    }

    /** Cola de reintento: al vencer el TTL devuelve el mensaje a la cola de trabajo. */
    @Bean
    public Queue pedidoCreadoRetryQueue() {
        return colaDeReintento(props.colas().pedidoCreado());
    }

    @Bean
    public Binding pedidoCreadoRetryBinding() {
        return BindingBuilder.bind(pedidoCreadoRetryQueue()).to(reintentosExchange())
                .with(props.colas().pedidoCreado().nombre());
    }

    @Bean
    public Queue pedidoCreadoDlq() {
        return dlq(props.colas().pedidoCreado());
    }

    @Bean
    public Binding pedidoCreadoDlqBinding() {
        return BindingBuilder.bind(pedidoCreadoDlq()).to(deadLetterExchange())
                .with(props.colas().pedidoCreado().nombre());
    }

    // ------------------------------------------------------------------ Caso de uso: documento.generado → email con comprobante

    @Bean
    public Queue documentoGeneradoQueue() {
        return colaDeTrabajo(props.colas().documentoGenerado());
    }

    @Bean
    public Binding documentoGeneradoBinding() {
        return BindingBuilder.bind(documentoGeneradoQueue()).to(eventosExchange())
                .with(props.routingKeys().documentoGenerado());
    }

    @Bean
    public Queue documentoGeneradoRetryQueue() {
        return colaDeReintento(props.colas().documentoGenerado());
    }

    @Bean
    public Binding documentoGeneradoRetryBinding() {
        return BindingBuilder.bind(documentoGeneradoRetryQueue()).to(reintentosExchange())
                .with(props.colas().documentoGenerado().nombre());
    }

    @Bean
    public Queue documentoGeneradoDlq() {
        return dlq(props.colas().documentoGenerado());
    }

    @Bean
    public Binding documentoGeneradoDlqBinding() {
        return BindingBuilder.bind(documentoGeneradoDlq()).to(deadLetterExchange())
                .with(props.colas().documentoGenerado().nombre());
    }

    // ------------------------------------------------------------------ Plantillas de colas

    /** Durable, con TTL y largo máximo: lo que expira o desborda (los más antiguos) va al DLX. */
    private Queue colaDeTrabajo(RabbitProperties.Cola cola) {
        return QueueBuilder.durable(cola.nombre())
                .deadLetterExchange(props.exchanges().deadLetter())
                .deadLetterRoutingKey(cola.nombre())
                .ttl(props.retencion().ttlColaMs())
                .maxLength(props.retencion().maxMensajesCola())
                .overflow(QueueBuilder.Overflow.dropHead)
                .build();
    }

    /** Sin consumidores: el mensaje espera el TTL y vuelve a su cola por el exchange por defecto (""). */
    private Queue colaDeReintento(RabbitProperties.Cola cola) {
        return QueueBuilder.durable(cola.reintento())
                .ttl(props.reintentos().esperaMs())
                .deadLetterExchange("")
                .deadLetterRoutingKey(cola.nombre())
                .build();
    }

    /** Cuarentena acotada por TTL y largo máximo. */
    private Queue dlq(RabbitProperties.Cola cola) {
        return QueueBuilder.durable(cola.dlq())
                .ttl(props.retencion().ttlDlqMs())
                .maxLength(props.retencion().maxMensajesDlq())
                .build();
    }
}
