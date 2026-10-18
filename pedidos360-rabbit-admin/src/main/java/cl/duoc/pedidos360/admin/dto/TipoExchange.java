package cl.duoc.pedidos360.admin.dto;

import java.util.Locale;

/** Tipos de exchange admitidos. Cualquier otro valor se rechaza con 400. */
public enum TipoExchange {
    DIRECT, TOPIC, FANOUT, HEADERS;

    /** Nombre del tipo en el protocolo AMQP ("direct", "topic", ...). */
    public String valorAmqp() {
        return name().toLowerCase(Locale.ROOT);
    }
}
