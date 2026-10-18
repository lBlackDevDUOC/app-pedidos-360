package cl.duoc.pedidos360.messaging;

import cl.duoc.pedidos360.messaging.config.RabbitProperties;

/** Propiedades de mensajería para pruebas unitarias (sin levantar Spring). */
public final class PropiedadesDePrueba {

    private PropiedadesDePrueba() {
    }

    public static RabbitProperties crear() {
        return new RabbitProperties(
                new RabbitProperties.Exchanges("test.events", "test.retry", "test.dlx"),
                new RabbitProperties.RoutingKeys("pedido.creado", "documento.generado"),
                new RabbitProperties.Colas(new RabbitProperties.Cola("test.q", "test.retry.q", "test.dlq")),
                new RabbitProperties.Reintentos(3, 5000),
                new RabbitProperties.Retencion(600000, 1000, 86400000, 5000));
    }
}
