package cl.duoc.pedidos360.audit.messaging.consumer;

/** El evento llegó, pero sus datos no permiten procesarlo. Error permanente: no se reintenta, va al DLT. */
public class EventoInvalidoException extends RuntimeException {

    public EventoInvalidoException(String mensaje) {
        super(mensaje);
    }
}
