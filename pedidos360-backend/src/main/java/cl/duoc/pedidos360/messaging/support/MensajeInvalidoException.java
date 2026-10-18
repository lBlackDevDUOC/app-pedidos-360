package cl.duoc.pedidos360.messaging.support;

/**
 * Error NO recuperable: el mensaje está mal formado o le faltan datos.
 * Reintentarlo daría siempre el mismo resultado, por eso va directo a la DLQ.
 */
public class MensajeInvalidoException extends RuntimeException {

    public MensajeInvalidoException(String mensaje) {
        super(mensaje);
    }

    public MensajeInvalidoException(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}
