package cl.duoc.pedidos360.model;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Entity
@Table(name = "pedidos")
public class Pedido {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Cliente dueño del pedido (columna cliente_id, clave foránea a clientes.id). */
    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "cliente_id", nullable = false)
    private Cliente cliente;

    private Double total;

    /** Productos del pedido (tabla pedido_items). Un pedido sin ítems es de monto libre (compatibilidad EP1-EP4). */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "pedido_items", joinColumns = @JoinColumn(name = "pedido_id"))
    private List<ItemPedido> items = new ArrayList<>();

    /** Se guarda como texto con {@link EstadoPedidoConverter}. Solo cambia a través de {@link #cambiarEstado}. */
    @Column(length = 20)
    private EstadoPedido estado = EstadoPedido.CREADO;

    private LocalDateTime fechaCreacion = LocalDateTime.now();
    private LocalDateTime fechaActualizacion;
    /** Momento en que pasó a ENTREGADO: junto con fechaCreacion da el lead time del pedido. */
    private LocalDateTime fechaEntrega;

    /** N° de comprobante emitido por ms-documentos (llega por RabbitMQ, evento documento.generado). */
    private String numeroComprobante;
    private LocalDateTime fechaComprobante;

    public Pedido() {}

    public Pedido(Cliente cliente, Double total) {
        this.cliente = cliente;
        this.total = total;
    }

    /** Pedido con productos del catálogo: el total es la suma de las líneas (no lo manda el cliente). */
    public Pedido(Cliente cliente, List<ItemPedido> items) {
        this.cliente = cliente;
        this.items = new ArrayList<>(items);
        this.total = Math.round(items.stream().mapToDouble(ItemPedido::subtotal).sum() * 100) / 100.0;
    }

    public boolean tieneItems() {
        return !items.isEmpty();
    }

    /**
     * Aplica la regla del ciclo de vida: solo se permiten las transiciones que declara {@link EstadoPedido}.
     *
     * @return el estado anterior (lo necesita el evento de cambio de estado)
     * @throws TransicionInvalidaException si el estado actual no permite pasar a {@code destino}
     */
    public EstadoPedido cambiarEstado(EstadoPedido destino) {
        EstadoPedido anterior = this.estado;
        if (!anterior.puedeCambiarA(destino)) {
            throw new TransicionInvalidaException(anterior, destino);
        }
        this.estado = destino;
        this.fechaActualizacion = LocalDateTime.now();
        if (destino == EstadoPedido.ENTREGADO) {
            this.fechaEntrega = this.fechaActualizacion;
        }
        return anterior;
    }

    // Getters y Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Cliente getCliente() { return cliente; }
    public void setCliente(Cliente cliente) { this.cliente = cliente; }

    public Double getTotal() { return total; }
    public void setTotal(Double total) { this.total = total; }

    public List<ItemPedido> getItems() { return Collections.unmodifiableList(items); }

    public EstadoPedido getEstado() { return estado; }

    public LocalDateTime getFechaCreacion() { return fechaCreacion; }
    public void setFechaCreacion(LocalDateTime fechaCreacion) { this.fechaCreacion = fechaCreacion; }

    public LocalDateTime getFechaActualizacion() { return fechaActualizacion; }

    public LocalDateTime getFechaEntrega() { return fechaEntrega; }

    public String getNumeroComprobante() { return numeroComprobante; }
    public void setNumeroComprobante(String numeroComprobante) { this.numeroComprobante = numeroComprobante; }

    public LocalDateTime getFechaComprobante() { return fechaComprobante; }
    public void setFechaComprobante(LocalDateTime fechaComprobante) { this.fechaComprobante = fechaComprobante; }
}
