package cl.duoc.pedidos360.admin.service;

/** El recurso (cola, exchange o binding) no existe en RabbitMQ → 404. */
public class RecursoNoEncontradoException extends RuntimeException {

    public RecursoNoEncontradoException(String mensaje) {
        super(mensaje);
    }
}
