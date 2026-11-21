package cl.duoc.pedidos360.kafkaadmin.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/** Contratos de entrada y salida de la API. Records inmutables; las entradas se validan con Jakarta Validation. */
public final class Dtos {

    private Dtos() {}

    /**
     * Nombre de tópico válido en Kafka: letras, números, '.', '_' y '-', máx. 249. Se prohíbe empezar con "__"
     * (reservado para los tópicos internos de Kafka).
     */
    public static final String PATRON_TOPICO = "^(?!__)[A-Za-z0-9._-]{1,249}$";
    public static final String MENSAJE_TOPICO =
            "solo letras, números, '.', '_' y '-' (máx. 249) y no puede empezar con '__' (reservado por Kafka)";
    public static final String PATRON_GRUPO = "^[A-Za-z0-9._-]{1,249}$";
    public static final String MENSAJE_GRUPO = "solo letras, números, '.', '_' y '-' (máx. 249)";

    // ---------------------------------------------------------------- Clúster
    public record BrokerResponse(int id, String host, int puerto, String rack, boolean controlador) {}

    public record ClusterResponse(String clusterId, Integer controladorId, int cantidadBrokers,
                                  List<BrokerResponse> brokers) {}

    // ---------------------------------------------------------------- Tópicos
    public record TopicoResumen(String nombre, int particiones, int factorReplicacion, boolean interno,
                                boolean protegido) {}

    /**
     * @param isr          réplicas sincronizadas (in-sync replicas). Si isr < réplicas, la partición está
     *                     sub-replicada: un broker está caído o atrasado.
     * @param offsetInicio primer offset disponible (lo anterior ya se borró por retención)
     * @param offsetFin    próximo offset a escribir (log-end offset)
     */
    public record ParticionResponse(int particion, Integer lider, List<Integer> replicas, List<Integer> isr,
                                    long offsetInicio, long offsetFin, long mensajes, boolean subReplicada) {}

    public record TopicoDetalle(String nombre, boolean interno, boolean protegido, int particiones,
                                int factorReplicacion, long mensajes, boolean subReplicado,
                                List<ParticionResponse> detalleParticiones, Map<String, String> configsModificadas) {}

    @Schema(description = "Cuerpo de POST /api/kafka-admin/topics")
    public record CrearTopicoRequest(
            @Schema(example = "pedidos.demo")
            @NotBlank(message = "es obligatorio")
            @Pattern(regexp = PATRON_TOPICO, message = MENSAJE_TOPICO)
            String nombre,

            @Schema(description = "Unidades de paralelismo: hasta N consumidores de un grupo leen en paralelo",
                    example = "3")
            @NotNull(message = "es obligatorio") @Min(value = 1, message = "mínimo 1") Integer particiones,

            @Schema(description = "Copias de cada partición en brokers distintos (máx. = cantidad de brokers)",
                    example = "3")
            @NotNull(message = "es obligatorio") @Min(value = 1, message = "mínimo 1")
            @Max(value = 3, message = "el clúster tiene 3 brokers: máximo 3") Short factorReplicacion,

            @Schema(description = "Configuraciones del tópico (lista blanca)",
                    example = "{\"retention.ms\":\"86400000\",\"cleanup.policy\":\"delete\",\"min.insync.replicas\":\"2\"}")
            Map<String, String> configs) {

        public Map<String, String> configsOVacio() {
            return configs == null ? Map.of() : configs;
        }

        @JsonIgnore
        @AssertTrue(message = "min.insync.replicas no puede ser mayor que factorReplicacion")
        public boolean isMinIsrConsistente() {
            String minIsr = configsOVacio().get("min.insync.replicas");
            if (minIsr == null || factorReplicacion == null) return true;
            try {
                return Integer.parseInt(minIsr) <= factorReplicacion;
            } catch (NumberFormatException e) {
                return true; // el formato lo informa la validación de configs del servicio
            }
        }
    }

    public record ConfigResponse(String nombre, String valor, String origen, boolean porDefecto, boolean soloLectura,
                                 boolean editable) {}

    public record ActualizarConfigsRequest(
            @Schema(example = "{\"retention.ms\":\"259200000\"}")
            @NotEmpty(message = "debe indicar al menos una configuración") Map<String, String> configs) {}

    public record AumentarParticionesRequest(
            @Schema(description = "Nuevo total de particiones (solo se puede aumentar)", example = "6")
            @NotNull(message = "es obligatorio") @Min(value = 1, message = "mínimo 1") Integer total) {}

    // ---------------------------------------------------------------- Consumer groups
    public record GrupoResumen(String groupId, String estado, int miembros, boolean simple) {}

    public record MiembroResponse(String memberId, String clientId, String host, List<String> particiones) {}

    /**
     * @param offsetConfirmado último offset que el grupo confirmó como procesado
     * @param offsetFin        último offset escrito en la partición
     * @param lag              mensajes pendientes = offsetFin - offsetConfirmado. Si crece, el grupo no da abasto.
     */
    public record OffsetResponse(String topico, int particion, long offsetConfirmado, long offsetFin, long lag) {}

    public record GrupoDetalle(String groupId, String estado, String coordinador, String estrategiaAsignacion,
                               List<MiembroResponse> miembros, List<OffsetResponse> offsets, long lagTotal,
                               Map<String, Long> lagPorTopico) {}

    public enum PuntoReset { EARLIEST, LATEST, TIMESTAMP }

    @Schema(description = "Reubica los offsets del grupo en un tópico (para reprocesar o saltar mensajes)")
    public record ResetOffsetsRequest(
            @NotBlank(message = "es obligatorio") @Pattern(regexp = PATRON_TOPICO, message = MENSAJE_TOPICO)
            String topico,
            @NotNull(message = "es obligatorio (EARLIEST, LATEST o TIMESTAMP)") PuntoReset a,
            @Schema(description = "Requerido si a=TIMESTAMP", example = "2026-11-20T00:00:00Z") Instant timestamp) {

        @JsonIgnore
        @AssertTrue(message = "timestamp es obligatorio cuando a=TIMESTAMP")
        public boolean isTimestampConsistente() {
            return a != PuntoReset.TIMESTAMP || timestamp != null;
        }
    }

    public record ResetOffsetsResponse(String groupId, String topico, PuntoReset a, List<OffsetResponse> nuevosOffsets) {}
}
