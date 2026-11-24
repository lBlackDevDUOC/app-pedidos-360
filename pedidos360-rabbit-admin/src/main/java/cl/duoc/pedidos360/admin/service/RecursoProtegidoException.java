package cl.duoc.pedidos360.admin.service;

/** El recurso pertenece a la topología de Pedidos360 y no se puede crear ni eliminar por la API → 409. */
public class RecursoProtegidoException extends RuntimeException {

    public RecursoProtegidoException(String mensaje) {
        super(mensaje);
    }
}
