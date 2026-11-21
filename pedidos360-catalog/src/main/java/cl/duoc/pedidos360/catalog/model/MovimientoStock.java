package cl.duoc.pedidos360.catalog.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;

/**
 * Registro de cada descuento o reposición de stock por pedido. Hace la operación idempotente: si ms-pedidos
 * reintenta "descontar stock del pedido 15", la restricción única impide descontarlo dos veces.
 */
@Entity
@Table(name = "movimientos_stock",
        uniqueConstraints = @UniqueConstraint(name = "uk_mov_pedido_producto_tipo",
                columnNames = {"pedidoId", "productoId", "tipo"}))
public class MovimientoStock {

    public enum Tipo { DESCUENTO, REPOSICION }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long pedidoId;
    @Column(nullable = false)
    private Long productoId;
    @Column(nullable = false)
    private Integer cantidad;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private Tipo tipo;
    private Instant fecha = Instant.now();

    protected MovimientoStock() {}

    public MovimientoStock(Long pedidoId, Long productoId, Integer cantidad, Tipo tipo) {
        this.pedidoId = pedidoId;
        this.productoId = productoId;
        this.cantidad = cantidad;
        this.tipo = tipo;
    }

    public Long getId() { return id; }
    public Long getPedidoId() { return pedidoId; }
    public Long getProductoId() { return productoId; }
    public Integer getCantidad() { return cantidad; }
    public Tipo getTipo() { return tipo; }
    public Instant getFecha() { return fecha; }
}
