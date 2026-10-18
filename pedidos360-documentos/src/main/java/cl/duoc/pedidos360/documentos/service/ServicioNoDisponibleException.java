package cl.duoc.pedidos360.documentos.service;

/** Error recuperable: un recurso externo no respondió. Vale la pena reintentar más tarde. */
public class ServicioNoDisponibleException extends RuntimeException {

    public ServicioNoDisponibleException(String mensaje) {
        super(mensaje);
    }
}
