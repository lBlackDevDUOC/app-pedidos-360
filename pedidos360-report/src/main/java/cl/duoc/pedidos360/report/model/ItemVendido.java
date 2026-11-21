package cl.duoc.pedidos360.report.model;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

/** Línea de un pedido tal como llegó en el evento. Alimenta el ranking de productos más vendidos. */
@Entity
@Table(name = "item_vendido", indexes = @Index(name = "ix_item_pedido", columnList = "pedidoId"))
public class ItemVendido {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long pedidoId;
    private Long productoId;
    private String nombre;
    private Integer cantidad;
    private Double monto;

    protected ItemVendido() {}

    public ItemVendido(Long pedidoId, Long productoId, String nombre, Integer cantidad, Double monto) {
        this.pedidoId = pedidoId;
        this.productoId = productoId;
        this.nombre = nombre;
        this.cantidad = cantidad;
        this.monto = monto;
    }

    public Long getPedidoId() { return pedidoId; }
    public Long getProductoId() { return productoId; }
    public String getNombre() { return nombre; }
    public Integer getCantidad() { return cantidad; }
    public Double getMonto() { return monto; }
}
