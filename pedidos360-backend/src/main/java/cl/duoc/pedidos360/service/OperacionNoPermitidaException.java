package cl.duoc.pedidos360.service;

/** El usuario está autenticado pero su rol no le permite esta acción sobre este pedido. En la API → 403. */
public class OperacionNoPermitidaException extends RuntimeException {

    public OperacionNoPermitidaException(String mensaje) {
        super(mensaje);
    }
}
