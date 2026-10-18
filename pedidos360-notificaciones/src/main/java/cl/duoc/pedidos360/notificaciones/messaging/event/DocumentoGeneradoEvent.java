package cl.duoc.pedidos360.notificaciones.messaging.event;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

/** Evento recibido desde ms-documentos (routing key documento.generado). */
public record DocumentoGeneradoEvent(
        String eventoId,
        @NotNull Long pedidoId,
        @NotBlank String numeroComprobante,
        String clienteEmail,
        String clienteNombre,
        Double total,
        Double neto,
        Double iva,
        LocalDateTime fechaEmision) {
}
