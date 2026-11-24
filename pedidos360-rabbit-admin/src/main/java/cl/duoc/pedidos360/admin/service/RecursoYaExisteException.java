package cl.duoc.pedidos360.admin.service;

/** Ya existe un recurso con ese nombre → 409. Evita redeclararlo con argumentos distintos. */
public class RecursoYaExisteException extends RuntimeException {

    public RecursoYaExisteException(String mensaje) {
        super(mensaje);
    }
}
