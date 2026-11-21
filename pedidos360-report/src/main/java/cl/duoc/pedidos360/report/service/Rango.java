package cl.duoc.pedidos360.report.service;

import java.time.Duration;
import java.util.Arrays;

/** Ventanas de tiempo aceptadas por los endpoints (?range=last24h). */
public enum Rango {

    LAST_24H("last24h", Duration.ofHours(24)),
    LAST_7D("last7d", Duration.ofDays(7)),
    LAST_30D("last30d", Duration.ofDays(30));

    private final String codigo;
    private final Duration duracion;

    Rango(String codigo, Duration duracion) {
        this.codigo = codigo;
        this.duracion = duracion;
    }

    public String codigo() { return codigo; }
    public Duration duracion() { return duracion; }

    public static Rango desde(String codigo) {
        return Arrays.stream(values())
                .filter(r -> r.codigo.equalsIgnoreCase(codigo))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "range inválido: '" + codigo + "'. Valores aceptados: last24h, last7d, last30d"));
    }
}
