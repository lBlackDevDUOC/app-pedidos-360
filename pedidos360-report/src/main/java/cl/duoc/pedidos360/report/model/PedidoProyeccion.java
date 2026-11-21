package cl.duoc.pedidos360.report.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;

/**
 * Proyección (read model) de un pedido, armada solo con los eventos de Kafka. ms-report nunca consulta la BD de
 * ms-pedidos: si ms-pedidos se cae, los reportes siguen respondiendo con lo último recibido ("sin bloquear el core").
 */
@Entity
@Table(name = "pedido_proyeccion", indexes = {
        @Index(name = "ix_proy_creacion", columnList = "fechaCreacion"),
        @Index(name = "ix_proy_entrega", columnList = "fechaEntrega"),
        @Index(name = "ix_proy_estado", columnList = "estado")})
public class PedidoProyeccion {

    /** Estados que ya no cambian: no cuentan como "activos" en el panel. */
    public static final java.util.Set<String> ESTADOS_FINALES = java.util.Set.of("ENTREGADO", "CANCELADO");

    @Id
    private Long pedidoId;

    @Column(length = 20, nullable = false)
    private String estado;

    private Double total;
    private String clienteNombre;
    private LocalDateTime fechaCreacion;
    private LocalDateTime fechaEntrega;
    /** Tiempo desde que se creó hasta que se entregó (solo pedidos ENTREGADO). */
    private Long leadTimeSegundos;
    /** Instante del último evento aplicado: sirve para descartar eventos viejos que lleguen tarde. */
    private Instant ultimoEventoEn;

    protected PedidoProyeccion() {}

    public PedidoProyeccion(Long pedidoId) {
        this.pedidoId = pedidoId;
    }

    /** ¿Este evento es más antiguo que lo que ya sabemos del pedido? */
    public boolean esAnteriorA(Instant ocurridoEn) {
        return ultimoEventoEn != null && ocurridoEn.isBefore(ultimoEventoEn);
    }

    public void aplicar(String estado, Double total, String clienteNombre, LocalDateTime fechaCreacion,
                        LocalDateTime fechaEntrega, Instant ocurridoEn) {
        this.estado = estado;
        this.total = total;
        if (clienteNombre != null) {
            this.clienteNombre = clienteNombre;
        }
        this.fechaCreacion = fechaCreacion;
        this.fechaEntrega = fechaEntrega;
        this.leadTimeSegundos = (fechaCreacion != null && fechaEntrega != null)
                ? Duration.between(fechaCreacion, fechaEntrega).toSeconds()
                : null;
        this.ultimoEventoEn = ocurridoEn;
    }

    public Long getPedidoId() { return pedidoId; }
    public String getEstado() { return estado; }
    public Double getTotal() { return total; }
    public String getClienteNombre() { return clienteNombre; }
    public LocalDateTime getFechaCreacion() { return fechaCreacion; }
    public LocalDateTime getFechaEntrega() { return fechaEntrega; }
    public Long getLeadTimeSegundos() { return leadTimeSegundos; }
    public Instant getUltimoEventoEn() { return ultimoEventoEn; }
}
