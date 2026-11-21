package cl.duoc.pedidos360.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/**
 * Línea de un pedido. Guarda una "foto" del nombre y del precio al momento de comprar: si mañana el Admin cambia
 * el precio en el catálogo, el pedido de hoy no cambia su total.
 */
@Embeddable
public class ItemPedido {

    @Column(nullable = false)
    private Long productoId;
    @Column(nullable = false, length = 120)
    private String nombre;
    @Column(nullable = false)
    private Integer cantidad;
    @Column(nullable = false)
    private Double precioUnitario;

    protected ItemPedido() {}

    public ItemPedido(Long productoId, String nombre, Integer cantidad, Double precioUnitario) {
        this.productoId = productoId;
        this.nombre = nombre;
        this.cantidad = cantidad;
        this.precioUnitario = precioUnitario;
    }

    public double subtotal() {
        return cantidad * precioUnitario;
    }

    public Long getProductoId() { return productoId; }
    public String getNombre() { return nombre; }
    public Integer getCantidad() { return cantidad; }
    public Double getPrecioUnitario() { return precioUnitario; }
}
