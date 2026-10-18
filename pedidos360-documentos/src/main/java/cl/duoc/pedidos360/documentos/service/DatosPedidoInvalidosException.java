package cl.duoc.pedidos360.documentos.service;

/** Error NO recuperable: con estos datos nunca se podrá emitir el comprobante (reintentar no sirve). */
public class DatosPedidoInvalidosException extends RuntimeException {

    public DatosPedidoInvalidosException(String mensaje) {
        super(mensaje);
    }
}
