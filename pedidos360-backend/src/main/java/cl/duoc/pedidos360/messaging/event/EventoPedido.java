package cl.duoc.pedidos360.messaging.event;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Evento de negocio publicado en el tópico {@code orders.events} cada vez que un pedido nace o cambia de estado.
 * Es la "fuente de verdad" para reportería y auditoría: lleva todo lo que necesitan los consumidores para
 * calcular KPIs sin tener que llamar de vuelta a ms-pedidos (desacoplamiento total).
 *
 * <p>Envelope (metadatos comunes a todo evento):
 * @param eventoId      UUID único: los consumidores lo usan para descartar duplicados (idempotencia)
 * @param tipo          OrderCreated, OrderAccepted, OrderPreparing, OrderDispatched, OrderDelivered, OrderCancelled
 * @param version       versión del esquema: si mañana cambia el DTO, el consumidor sabe cómo leerlo
 * @param ocurridoEn    instante exacto del hecho (UTC)
 * @param origen        microservicio que lo emitió
 * @param traceId       identifica la operación que originó el evento (para seguirla en los logs de todos los ms)
 * @param correlationId agrupa todos los eventos de un mismo pedido ("pedido-15")
 *
 * <p>Payload (datos del pedido):
 * @param pedidoId       también es la KEY del mensaje: todos los eventos de un pedido van a la misma partición
 *                       y se consumen en orden (CREADO antes que ACEPTADO)
 * @param estadoAnterior null en OrderCreated
 * @param fechaCreacion  junto con fechaEntrega permite calcular el lead time
 * @param actorId        quién provocó el cambio (oid de Entra ID)
 * @param items          productos del pedido (vacío en pedidos de monto libre)
 */
public record EventoPedido(
        String eventoId,
        String tipo,
        int version,
        Instant ocurridoEn,
        String origen,
        String traceId,
        String correlationId,
        Long pedidoId,
        String clienteId,
        String clienteNombre,
        Double total,
        String estadoAnterior,
        String estado,
        LocalDateTime fechaCreacion,
        LocalDateTime fechaEntrega,
        String actorId,
        List<Item> items) {

    /** Línea del pedido: ms-report la usa para el ranking de productos más vendidos. */
    public record Item(Long productoId, String nombre, Integer cantidad, Double precioUnitario) {}

    public static final int VERSION_ACTUAL = 1;
}
