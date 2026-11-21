package cl.duoc.pedidos360.kafkaadmin.service;

import cl.duoc.pedidos360.kafkaadmin.config.AdminProperties;
import cl.duoc.pedidos360.kafkaadmin.service.AdminExceptions.ConfiguracionInvalida;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Valida las configuraciones de tópico ANTES de enviarlas al clúster: solo claves de la lista blanca y valores
 * con sentido. Así la API responde un 400 claro en vez de un error críptico del broker, y nadie puede, por
 * ejemplo, dejar un tópico con min.insync.replicas mayor que sus réplicas (todas las escrituras fallarían).
 */
@Component
public class ValidadorConfigs {

    private static final Set<String> NUMERICAS_MS = Set.of(
            "retention.ms", "segment.ms", "delete.retention.ms", "min.compaction.lag.ms");
    private static final Set<String> POLITICAS = Set.of("delete", "compact", "compact,delete", "delete,compact");
    private static final Set<String> COMPRESIONES = Set.of("producer", "uncompressed", "gzip", "snappy", "lz4", "zstd");

    private final AdminProperties props;

    public ValidadorConfigs(AdminProperties props) {
        this.props = props;
    }

    /** @param factorReplicacion réplicas del tópico (para validar min.insync.replicas) */
    public void validar(Map<String, String> configs, int factorReplicacion) {
        List<String> errores = new ArrayList<>();
        configs.forEach((clave, valor) -> {
            if (!props.configsEditables().contains(clave)) {
                errores.add(clave + ": no es editable desde la API. Permitidas: " + props.configsEditables());
                return;
            }
            if (valor == null || valor.isBlank()) {
                errores.add(clave + ": el valor no puede estar vacío");
                return;
            }
            String v = valor.trim();
            if (NUMERICAS_MS.contains(clave) || clave.equals("retention.bytes")) {
                Long numero = comoLong(v);
                if (numero == null || numero < -1) {
                    errores.add(clave + ": debe ser un número >= -1 (-1 = sin límite)");
                } else if (clave.equals("segment.ms") && numero < 60_000) {
                    errores.add(clave + ": mínimo 60000 (1 minuto)");
                }
            } else if (clave.equals("min.insync.replicas")) {
                Long numero = comoLong(v);
                if (numero == null || numero < 1 || numero > factorReplicacion) {
                    errores.add(clave + ": debe estar entre 1 y el factor de replicación del tópico ("
                            + factorReplicacion + ")");
                }
            } else if (clave.equals("max.message.bytes")) {
                Long numero = comoLong(v);
                if (numero == null || numero < 1024 || numero > 10_485_760) {
                    errores.add(clave + ": debe estar entre 1024 y 10485760 (10 MB)");
                }
            } else if (clave.equals("cleanup.policy") && !POLITICAS.contains(v.replace(" ", ""))) {
                errores.add(clave + ": valores permitidos " + POLITICAS);
            } else if (clave.equals("compression.type") && !COMPRESIONES.contains(v)) {
                errores.add(clave + ": valores permitidos " + COMPRESIONES);
            }
        });
        if (!errores.isEmpty()) {
            throw new ConfiguracionInvalida(String.join("; ", errores));
        }
    }

    private static Long comoLong(String valor) {
        try {
            return Long.parseLong(valor);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
