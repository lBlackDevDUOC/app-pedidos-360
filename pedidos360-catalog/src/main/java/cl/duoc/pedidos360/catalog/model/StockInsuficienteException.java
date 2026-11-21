package cl.duoc.pedidos360.catalog.model;

/** No hay unidades suficientes para el pedido. En la API → 409 (el pedido no se acepta). */
public class StockInsuficienteException extends RuntimeException {

    private final Long productoId;
    private final int disponible;
    private final int solicitado;

    public StockInsuficienteException(Producto producto, int solicitado) {
        super("Stock insuficiente de '" + producto.getNombre() + "': hay " + producto.getStock()
                + ", se piden " + solicitado);
        this.productoId = producto.getId();
        this.disponible = producto.getStock();
        this.solicitado = solicitado;
    }

    public Long getProductoId() { return productoId; }
    public int getDisponible() { return disponible; }
    public int getSolicitado() { return solicitado; }
}
