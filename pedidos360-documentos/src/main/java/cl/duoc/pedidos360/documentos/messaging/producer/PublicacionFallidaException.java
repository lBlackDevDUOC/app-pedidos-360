package cl.duoc.pedidos360.documentos.messaging.producer;

/** No se pudo publicar un evento (broker caído o sin conexión). Es recuperable: se reintenta. */
public class PublicacionFallidaException extends RuntimeException {

    public PublicacionFallidaException(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}
