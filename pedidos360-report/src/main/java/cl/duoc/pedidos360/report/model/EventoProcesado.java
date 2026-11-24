package cl.duoc.pedidos360.report.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Registro de eventos ya aplicados (idempotencia). Kafka entrega "al menos una vez": tras un rebalanceo o un
 * reinicio, un evento puede llegar dos veces. Si su eventoId ya está aquí, se ignora y el KPI no se duplica.
 */
@Entity
@Table(name = "evento_procesado")
public class EventoProcesado {

    @Id
    private String eventoId;
    private String tipo;
    private Long pedidoId;
    private Instant procesadoEn;

    protected EventoProcesado() {}

    public EventoProcesado(String eventoId, String tipo, Long pedidoId) {
        this.eventoId = eventoId;
        this.tipo = tipo;
        this.pedidoId = pedidoId;
        this.procesadoEn = Instant.now();
    }

    public String getEventoId() { return eventoId; }
    public String getTipo() { return tipo; }
    public Long getPedidoId() { return pedidoId; }
    public Instant getProcesadoEn() { return procesadoEn; }
}
