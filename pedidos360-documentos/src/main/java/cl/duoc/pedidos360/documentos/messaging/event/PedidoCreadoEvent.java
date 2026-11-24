package cl.duoc.pedidos360.documentos.messaging.event;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

/**
 * Evento recibido desde ms-pedidos (routing key pedido.creado). Cada microservicio tiene su propia
 * copia del contrato JSON: no se comparten clases para que los despliegues sean independientes.
 */
public record PedidoCreadoEvent(
        String eventoId,
        @NotNull Long pedidoId,
        String clienteNombre,
        String clienteEmail,
        @NotNull Double total,
        String estado,
        LocalDateTime fechaCreacion) {
}
