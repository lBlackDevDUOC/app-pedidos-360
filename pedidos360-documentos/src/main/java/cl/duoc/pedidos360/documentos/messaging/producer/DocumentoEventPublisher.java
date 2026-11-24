package cl.duoc.pedidos360.documentos.messaging.producer;

import cl.duoc.pedidos360.documentos.model.Comprobante;

/** Puerto de salida: anuncia que se emitió un comprobante. */
public interface DocumentoEventPublisher {

    /**
     * Publica el evento documento.generado.
     *
     * @throws PublicacionFallidaException si el broker no aceptó el mensaje (error recuperable)
     */
    void publicarDocumentoGenerado(Comprobante comprobante);
}
