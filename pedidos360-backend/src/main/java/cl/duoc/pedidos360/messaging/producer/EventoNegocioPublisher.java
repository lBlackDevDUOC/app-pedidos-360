package cl.duoc.pedidos360.messaging.producer;

import cl.duoc.pedidos360.model.EstadoPedido;
import cl.duoc.pedidos360.model.Pedido;
import cl.duoc.pedidos360.model.Actor;

/**
 * Puerto de salida para los eventos de negocio que alimentan la analítica y la auditoría (streaming).
 * PedidoService solo conoce esta interfaz; que por detrás sea Kafka, con qué tópico y qué key, es asunto
 * de la implementación ({@link KafkaEventoNegocioPublisher}).
 *
 * <p>Diferencia con {@link PedidoEventPublisher} (RabbitMQ): RabbitMQ lleva COMANDOS de trabajo que un solo
 * consumidor ejecuta y borra (generar boleta, enviar correo). Kafka lleva HECHOS que quedan guardados en un log
 * y que cualquier número de consumidores puede leer (y releer) a su ritmo: reportería, auditoría, etc.
 */
public interface EventoNegocioPublisher {

    /** Nunca lanza excepción: si Kafka está caído el pedido igual se crea; el fallo queda en el log. */
    void pedidoCreado(Pedido pedido, Actor actor);

    void estadoCambiado(Pedido pedido, EstadoPedido anterior, Actor actor);
}
