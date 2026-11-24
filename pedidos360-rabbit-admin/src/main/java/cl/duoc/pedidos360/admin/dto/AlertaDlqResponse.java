package cl.duoc.pedidos360.admin.dto;

import java.time.Instant;

/** Una DLQ que alcanzó el umbral de mensajes en cuarentena. */
public record AlertaDlqResponse(String cola, long mensajes, int umbral, Instant detectadaEn) {
}
