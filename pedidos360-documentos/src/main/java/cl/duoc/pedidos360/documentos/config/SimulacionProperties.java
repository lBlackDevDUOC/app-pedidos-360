package cl.duoc.pedidos360.documentos.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Totales de pedido que disparan fallos simulados (solo para la demostración en vivo).
 * Se apaga con app.simulacion.habilitada=false.
 */
@ConfigurationProperties(prefix = "app.simulacion")
public record SimulacionProperties(
        boolean habilitada,
        double totalFallaUnaVez,
        double totalFallaSiempre,
        double totalDatosInvalidos) {
}
