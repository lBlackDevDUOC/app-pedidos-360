package cl.duoc.pedidos360.messaging.event;

import java.time.Instant;
import java.util.List;

/**
 * Registro de auditoría publicado en el tópico {@code audit.timeline}: responde quién, qué, cuándo y desde dónde.
 * A diferencia de {@link EventoPedido} (pensado para cálculos), este está pensado para ser leído por una persona
 * (Auditor) en la línea de tiempo.
 *
 * @param eventoId      también es la KEY del mensaje. El tópico usa cleanup.policy=compact,delete: la compactación
 *                      solo elimina copias del MISMO evento (reintentos), nunca la historia de un pedido.
 * @param accion        qué pasó: PEDIDO_CREADO, PEDIDO_ESTADO_CAMBIADO
 * @param entidad       sobre qué tipo de cosa (PEDIDO)
 * @param entidadId     sobre cuál (id del pedido)
 * @param detalle       texto legible: "CREADO → ACEPTADO"
 * @param actorId       quién (oid de Entra ID)
 * @param actorRoles    con qué rol actuó
 * @param ip            desde dónde (IP de origen)
 * @param userAgent     desde dónde (navegador / cliente HTTP)
 */
public record EventoAuditoria(
        String eventoId,
        int version,
        Instant ocurridoEn,
        String origen,
        String traceId,
        String correlationId,
        String accion,
        String entidad,
        String entidadId,
        String detalle,
        String actorId,
        String actorNombre,
        List<String> actorRoles,
        String ip,
        String userAgent) {

    public static final int VERSION_ACTUAL = 1;
}
