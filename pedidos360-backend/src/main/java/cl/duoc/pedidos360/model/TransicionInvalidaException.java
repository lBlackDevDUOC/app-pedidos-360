package cl.duoc.pedidos360.model;

/** Se intentó pasar un pedido a un estado que su estado actual no permite (ej. CREADO → DESPACHADO). */
public class TransicionInvalidaException extends RuntimeException {

    private final EstadoPedido actual;
    private final EstadoPedido destino;

    public TransicionInvalidaException(EstadoPedido actual, EstadoPedido destino) {
        super("No se puede pasar de " + actual + " a " + destino
                + (actual.esFinal() ? " (" + actual + " es un estado final)" : ". Permitidos: " + actual.siguientes()));
        this.actual = actual;
        this.destino = destino;
    }

    public EstadoPedido getActual() { return actual; }
    public EstadoPedido getDestino() { return destino; }
}
