package cl.duoc.pedidos360.messaging.event;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

/**
 * Evento que publica ms-documentos al emitir el comprobante de un pedido (routing key documento.generado).
 * Las anotaciones de validación definen qué campos son obligatorios para procesarlo.
 */
public record DocumentoGeneradoEvent(
        String eventoId,
        @NotNull Long pedidoId,
        @NotBlank String numeroComprobante,
        String clienteEmail,
        String clienteNombre,
        Double total,
        Double neto,
        Double iva,
        @NotNull LocalDateTime fechaEmision) {
}
