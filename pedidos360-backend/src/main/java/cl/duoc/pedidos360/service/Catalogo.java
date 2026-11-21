package cl.duoc.pedidos360.service;

import cl.duoc.pedidos360.model.ItemPedido;

import java.util.Collection;
import java.util.List;

/**
 * Puerto hacia ms-catalog. PedidoService no sabe que por detrás hay una llamada HTTP; la implementación vive en
 * el paquete integration (RestCatalogoClient). Es comunicación SÍNCRONA a propósito: para aceptar un pedido hay
 * que saber en ese momento si hay stock (una cola no puede responder "sí/no" en la misma petición).
 */
public interface Catalogo {

    record ProductoCatalogo(Long id, String nombre, Double precio, Integer stock, boolean activo) {}

    List<ProductoCatalogo> productos(Collection<Long> ids);

    /** @throws StockInsuficienteException si alguna línea no alcanza (no se descuenta nada) */
    void descontarStock(Long pedidoId, List<ItemPedido> items);

    void reponerStock(Long pedidoId);

    /** No hay stock para aceptar el pedido. En la API → 409. */
    class StockInsuficienteException extends RuntimeException {
        public StockInsuficienteException(String mensaje) { super(mensaje); }
    }

    /** Producto inexistente o inactivo al armar un pedido. En la API → 400. */
    class ProductoNoDisponibleException extends RuntimeException {
        public ProductoNoDisponibleException(String mensaje) { super(mensaje); }
    }

    /** ms-catalog no respondió. En la API → 503 (el pedido no cambia). */
    class CatalogoNoDisponibleException extends RuntimeException {
        public CatalogoNoDisponibleException(String mensaje, Throwable causa) { super(mensaje, causa); }
    }
}
