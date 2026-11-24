package cl.duoc.pedidos360.catalog.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;

@Entity
@Table(name = "productos")
public class Producto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false, length = 40)
    private String sku;

    @Column(nullable = false, length = 120)
    private String nombre;

    @Column(length = 500)
    private String descripcion;

    @Column(length = 60)
    private String categoria;

    @Column(nullable = false)
    private Double precio;

    @Column(nullable = false)
    private Integer stock;

    private boolean activo = true;
    private Instant actualizadoEn = Instant.now();

    /** Bloqueo optimista: si dos operadores editan el mismo producto a la vez, el segundo recibe un conflicto. */
    @Version
    private Long version;

    protected Producto() {}

    public Producto(String sku, String nombre, String descripcion, String categoria, Double precio, Integer stock) {
        this.sku = sku;
        this.nombre = nombre;
        this.descripcion = descripcion;
        this.categoria = categoria;
        this.precio = precio;
        this.stock = stock;
    }

    /** @throws StockInsuficienteException si no alcanza: el stock nunca queda negativo */
    public void descontar(int cantidad) {
        if (cantidad > stock) {
            throw new StockInsuficienteException(this, cantidad);
        }
        stock -= cantidad;
        actualizadoEn = Instant.now();
    }

    public void reponer(int cantidad) {
        stock += cantidad;
        actualizadoEn = Instant.now();
    }

    public void actualizar(String nombre, String descripcion, String categoria, Double precio, Integer stock,
                           Boolean activo) {
        if (nombre != null) this.nombre = nombre;
        if (descripcion != null) this.descripcion = descripcion;
        if (categoria != null) this.categoria = categoria;
        if (precio != null) this.precio = precio;
        if (stock != null) this.stock = stock;
        if (activo != null) this.activo = activo;
        this.actualizadoEn = Instant.now();
    }

    public Long getId() { return id; }
    public String getSku() { return sku; }
    public String getNombre() { return nombre; }
    public String getDescripcion() { return descripcion; }
    public String getCategoria() { return categoria; }
    public Double getPrecio() { return precio; }
    public Integer getStock() { return stock; }
    public boolean isActivo() { return activo; }
    public Instant getActualizadoEn() { return actualizadoEn; }
    public Long getVersion() { return version; }
}
