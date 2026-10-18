package cl.duoc.pedidos360.service;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** El pedido no existe o pertenece a otro cliente. En la API REST se traduce a 404. */
@ResponseStatus(code = HttpStatus.NOT_FOUND, reason = "Pedido no encontrado")
public class PedidoNoEncontradoException extends RuntimeException {

    public PedidoNoEncontradoException(Long pedidoId) {
        super("Pedido no encontrado: " + pedidoId);
    }
}
