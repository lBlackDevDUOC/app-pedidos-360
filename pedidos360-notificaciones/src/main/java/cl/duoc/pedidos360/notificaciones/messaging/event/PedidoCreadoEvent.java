package cl.duoc.pedidos360.notificaciones.messaging.event;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

/** Evento recibido desde ms-pedidos (routing key pedido.creado). */
public record PedidoCreadoEvent(
        String eventoId,
        @NotNull Long pedidoId,
        String clienteNombre,
        String clienteEmail,
        Double total,
        String estado,
        LocalDateTime fechaCreacion) {
}
