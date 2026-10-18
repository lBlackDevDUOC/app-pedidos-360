package cl.duoc.pedidos360.admin.dto;

import java.util.Map;

/**
 * Estado de una cola. mensajesListos = esperando consumidor; mensajesSinConfirmar = entregados
 * y aún sin ACK (en proceso). Si mensajesListos crece y no baja, los consumidores no dan abasto.
 */
public record ColaResponse(
        String nombre,
        boolean durable,
        long mensajes,
        long mensajesListos,
        long mensajesSinConfirmar,
        int consumidores,
        boolean esDlq,
        Map<String, Object> argumentos) {
}
