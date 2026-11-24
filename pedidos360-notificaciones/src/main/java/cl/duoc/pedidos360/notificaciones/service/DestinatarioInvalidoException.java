package cl.duoc.pedidos360.notificaciones.service;

/** Error NO recuperable: el cliente no tiene un email válido, reintentar no lo soluciona. */
public class DestinatarioInvalidoException extends RuntimeException {

    public DestinatarioInvalidoException(String mensaje) {
        super(mensaje);
    }
}
