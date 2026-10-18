package cl.duoc.pedidos360.messaging.event;

import java.time.LocalDateTime;

/**
 * Evento publicado cuando un cliente crea un pedido (routing key pedido.creado).
 * Lleva todos los datos que necesitan los consumidores para no tener que llamar de vuelta a ms-pedidos.
 *
 * @param eventoId identificador único del evento (también es el messageId del mensaje AMQP)
 */
public record PedidoCreadoEvent(
        String eventoId,
        Long pedidoId,
        String clienteNombre,
        String clienteEmail,
        Double total,
        String estado,
        LocalDateTime fechaCreacion) {
}
