package cl.duoc.pedidos360.documentos.model;

import java.time.LocalDateTime;

/** Comprobante (boleta) emitido para un pedido. El neto y el IVA (19%) se calculan desde el total. */
public record Comprobante(
        String numero,
        Long pedidoId,
        String clienteNombre,
        String clienteEmail,
        double total,
        double neto,
        double iva,
        LocalDateTime fechaEmision) {
}
