package cl.duoc.pedidos360.documentos.messaging.event;

import java.time.LocalDateTime;

/** Evento publicado al emitir un comprobante (routing key documento.generado). */
public record DocumentoGeneradoEvent(
        String eventoId,
        Long pedidoId,
        String numeroComprobante,
        String clienteEmail,
        String clienteNombre,
        Double total,
        Double neto,
        Double iva,
        LocalDateTime fechaEmision) {
}
