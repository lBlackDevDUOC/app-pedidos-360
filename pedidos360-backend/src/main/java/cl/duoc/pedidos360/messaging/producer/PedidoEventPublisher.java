package cl.duoc.pedidos360.messaging.producer;

import cl.duoc.pedidos360.model.Pedido;

/**
 * Puerto de salida para eventos de pedidos. La lógica de negocio solo conoce esta interfaz;
 * cómo y a dónde se publica (RabbitMQ, exchange, routing key) queda en la implementación.
 */
public interface PedidoEventPublisher {

    /** Anuncia que se creó un pedido. Nunca lanza excepción: un fallo de mensajería no anula el pedido. */
    void publicarPedidoCreado(Pedido pedido);
}
