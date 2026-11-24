package cl.duoc.pedidos360.admin.service;

/** La petición es válida en formato pero su configuración no tiene sentido en el broker → 422. */
public class ConfiguracionInvalidaException extends RuntimeException {

    public ConfiguracionInvalidaException(String mensaje) {
        super(mensaje);
    }
}
