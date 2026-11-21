package cl.duoc.pedidos360.audit.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Una entrada de la línea de tiempo. Inmutable: no tiene setters porque la auditoría nunca se edita.
 * El id es el eventoId del mensaje de Kafka: si el mismo evento llega dos veces, la PK lo impide (idempotencia).
 */
@Entity
@Table(name = "registro_auditoria", indexes = {
        @Index(name = "ix_aud_ocurrido", columnList = "ocurridoEn"),
        @Index(name = "ix_aud_actor", columnList = "actorId"),
        @Index(name = "ix_aud_entidad", columnList = "entidad, entidadId"),
        @Index(name = "ix_aud_accion", columnList = "accion")})
public class RegistroAuditoria {

    @Id
    @Column(length = 64)
    private String eventoId;

    /** Tópico del que vino: audit.timeline (acción de un usuario) u orders.events (hecho de negocio). */
    @Column(length = 64, nullable = false)
    private String fuente;

    @Column(nullable = false)
    private Instant ocurridoEn;

    @Column(length = 64, nullable = false)
    private String accion;
    @Column(length = 32, nullable = false)
    private String entidad;
    @Column(length = 64, nullable = false)
    private String entidadId;
    @Column(length = 500)
    private String detalle;

    @Column(length = 128)
    private String actorId;
    private String actorNombre;
    private String actorRoles;
    @Column(length = 64)
    private String ip;
    @Column(length = 500)
    private String userAgent;

    private String origen;
    private String traceId;
    private String correlationId;
    private Instant recibidoEn;

    protected RegistroAuditoria() {}

    public RegistroAuditoria(String eventoId, String fuente, Instant ocurridoEn, String accion, String entidad,
                             String entidadId, String detalle, String actorId, String actorNombre, String actorRoles,
                             String ip, String userAgent, String origen, String traceId, String correlationId) {
        this.eventoId = eventoId;
        this.fuente = fuente;
        this.ocurridoEn = ocurridoEn;
        this.accion = accion;
        this.entidad = entidad;
        this.entidadId = entidadId;
        this.detalle = recortar(detalle, 500);
        this.actorId = actorId;
        this.actorNombre = actorNombre;
        this.actorRoles = actorRoles;
        this.ip = ip;
        this.userAgent = recortar(userAgent, 500);
        this.origen = origen;
        this.traceId = traceId;
        this.correlationId = correlationId;
        this.recibidoEn = Instant.now();
    }

    private static String recortar(String texto, int max) {
        return texto == null || texto.length() <= max ? texto : texto.substring(0, max);
    }

    public String getEventoId() { return eventoId; }
    public String getFuente() { return fuente; }
    public Instant getOcurridoEn() { return ocurridoEn; }
    public String getAccion() { return accion; }
    public String getEntidad() { return entidad; }
    public String getEntidadId() { return entidadId; }
    public String getDetalle() { return detalle; }
    public String getActorId() { return actorId; }
    public String getActorNombre() { return actorNombre; }
    public String getActorRoles() { return actorRoles; }
    public String getIp() { return ip; }
    public String getUserAgent() { return userAgent; }
    public String getOrigen() { return origen; }
    public String getTraceId() { return traceId; }
    public String getCorrelationId() { return correlationId; }
    public Instant getRecibidoEn() { return recibidoEn; }
}
