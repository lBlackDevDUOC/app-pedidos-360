package cl.duoc.pedidos360.kafkaadmin.service;

import cl.duoc.pedidos360.kafkaadmin.config.AdminProperties;
import cl.duoc.pedidos360.kafkaadmin.dto.Dtos.ActualizarConfigsRequest;
import cl.duoc.pedidos360.kafkaadmin.dto.Dtos.BrokerResponse;
import cl.duoc.pedidos360.kafkaadmin.dto.Dtos.ClusterResponse;
import cl.duoc.pedidos360.kafkaadmin.dto.Dtos.ConfigResponse;
import cl.duoc.pedidos360.kafkaadmin.dto.Dtos.CrearTopicoRequest;
import cl.duoc.pedidos360.kafkaadmin.dto.Dtos.GrupoDetalle;
import cl.duoc.pedidos360.kafkaadmin.dto.Dtos.GrupoResumen;
import cl.duoc.pedidos360.kafkaadmin.dto.Dtos.MiembroResponse;
import cl.duoc.pedidos360.kafkaadmin.dto.Dtos.OffsetResponse;
import cl.duoc.pedidos360.kafkaadmin.dto.Dtos.ParticionResponse;
import cl.duoc.pedidos360.kafkaadmin.dto.Dtos.ResetOffsetsRequest;
import cl.duoc.pedidos360.kafkaadmin.dto.Dtos.ResetOffsetsResponse;
import cl.duoc.pedidos360.kafkaadmin.dto.Dtos.TopicoDetalle;
import cl.duoc.pedidos360.kafkaadmin.dto.Dtos.TopicoResumen;
import cl.duoc.pedidos360.kafkaadmin.service.AdminExceptions.ClusterNoDisponible;
import cl.duoc.pedidos360.kafkaadmin.service.AdminExceptions.ConfiguracionInvalida;
import cl.duoc.pedidos360.kafkaadmin.service.AdminExceptions.OperacionNoPermitida;
import cl.duoc.pedidos360.kafkaadmin.service.AdminExceptions.RecursoNoEncontrado;
import cl.duoc.pedidos360.kafkaadmin.service.AdminExceptions.RecursoYaExiste;
import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.AlterConfigOp;
import org.apache.kafka.clients.admin.Config;
import org.apache.kafka.clients.admin.ConfigEntry;
import org.apache.kafka.clients.admin.ConsumerGroupDescription;
import org.apache.kafka.clients.admin.ConsumerGroupListing;
import org.apache.kafka.clients.admin.DescribeClusterResult;
import org.apache.kafka.clients.admin.ListOffsetsResult.ListOffsetsResultInfo;
import org.apache.kafka.clients.admin.ListTopicsOptions;
import org.apache.kafka.clients.admin.NewPartitions;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.admin.OffsetSpec;
import org.apache.kafka.clients.admin.TopicDescription;
import org.apache.kafka.clients.admin.TopicListing;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.ConsumerGroupState;
import org.apache.kafka.common.KafkaFuture;
import org.apache.kafka.common.Node;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.TopicPartitionInfo;
import org.apache.kafka.common.config.ConfigResource;
import org.apache.kafka.common.errors.GroupIdNotFoundException;
import org.apache.kafka.common.errors.GroupNotEmptyException;
import org.apache.kafka.common.errors.InvalidConfigurationException;
import org.apache.kafka.common.errors.InvalidPartitionsException;
import org.apache.kafka.common.errors.InvalidReplicationFactorException;
import org.apache.kafka.common.errors.InvalidRequestException;
import org.apache.kafka.common.errors.PolicyViolationException;
import org.apache.kafka.common.errors.TopicExistsException;
import org.apache.kafka.common.errors.UnknownTopicOrPartitionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Toda la lógica de administración de Kafka. Los controladores llaman métodos de alto nivel (crear tópico, ver lag)
 * y no conocen el AdminClient. Cada operación se espera con timeout y los errores del clúster se traducen a
 * excepciones de negocio con mensajes claros.
 */
@Service
public class KafkaAdminService {

    private static final Logger log = LoggerFactory.getLogger(KafkaAdminService.class);

    private final Admin admin;
    private final AdminProperties props;
    private final ValidadorConfigs validador;

    public KafkaAdminService(Admin admin, AdminProperties props, ValidadorConfigs validador) {
        this.admin = admin;
        this.props = props;
        this.validador = validador;
    }

    // ================================================================ Clúster

    public ClusterResponse cluster() {
        DescribeClusterResult resultado = admin.describeCluster();
        Node controlador = esperar(resultado.controller(), "describir el clúster");
        List<BrokerResponse> brokers = esperar(resultado.nodes(), "describir el clúster").stream()
                .sorted(Comparator.comparingInt(Node::id))
                .map(n -> new BrokerResponse(n.id(), n.host(), n.port(), n.rack(),
                        controlador != null && controlador.id() == n.id()))
                .toList();
        return new ClusterResponse(esperar(resultado.clusterId(), "describir el clúster"),
                controlador == null ? null : controlador.id(), brokers.size(), brokers);
    }

    // ================================================================ Tópicos

    public List<TopicoResumen> listarTopicos(boolean incluirInternos) {
        var listados = esperar(admin.listTopics(new ListTopicsOptions().listInternal(incluirInternos)).listings(),
                "listar tópicos");
        Set<String> nombres = listados.stream().map(TopicListing::name).collect(Collectors.toSet());
        if (nombres.isEmpty()) return List.of();
        Map<String, TopicDescription> descripciones = esperar(admin.describeTopics(nombres).allTopicNames(),
                "describir tópicos");
        return descripciones.values().stream()
                .sorted(Comparator.comparing(TopicDescription::name))
                .map(d -> new TopicoResumen(d.name(), d.partitions().size(), factorReplicacion(d), d.isInternal(),
                        props.esProtegido(d.name())))
                .toList();
    }

    public TopicoDetalle obtenerTopico(String nombre) {
        TopicDescription descripcion = describir(nombre);
        List<TopicPartition> particiones = descripcion.partitions().stream()
                .map(p -> new TopicPartition(nombre, p.partition())).toList();
        Map<TopicPartition, Long> inicio = offsets(particiones, OffsetSpec.earliest());
        Map<TopicPartition, Long> fin = offsets(particiones, OffsetSpec.latest());

        List<ParticionResponse> detalle = descripcion.partitions().stream()
                .sorted(Comparator.comparingInt(TopicPartitionInfo::partition))
                .map(p -> {
                    TopicPartition tp = new TopicPartition(nombre, p.partition());
                    long desde = inicio.getOrDefault(tp, 0L);
                    long hasta = fin.getOrDefault(tp, 0L);
                    return new ParticionResponse(p.partition(), p.leader() == null ? null : p.leader().id(),
                            ids(p.replicas()), ids(p.isr()), desde, hasta, hasta - desde,
                            p.isr().size() < p.replicas().size());
                })
                .toList();

        Map<String, String> modificadas = configsDe(nombre).stream()
                .filter(c -> !c.porDefecto())
                .collect(Collectors.toMap(ConfigResponse::nombre, ConfigResponse::valor, (a, b) -> a, TreeMap::new));

        return new TopicoDetalle(nombre, descripcion.isInternal(), props.esProtegido(nombre), detalle.size(),
                factorReplicacion(descripcion), detalle.stream().mapToLong(ParticionResponse::mensajes).sum(),
                detalle.stream().anyMatch(ParticionResponse::subReplicada), detalle, modificadas);
    }

    public TopicoDetalle crearTopico(CrearTopicoRequest request) {
        if (request.particiones() > props.maxParticiones()) {
            throw new ConfiguracionInvalida("particiones: máximo " + props.maxParticiones());
        }
        int brokers = cluster().cantidadBrokers();
        if (request.factorReplicacion() > brokers) {
            throw new ConfiguracionInvalida("factorReplicacion " + request.factorReplicacion()
                    + " no puede superar la cantidad de brokers activos (" + brokers + ")");
        }
        validador.validar(request.configsOVacio(), request.factorReplicacion());

        NewTopic nuevo = new NewTopic(request.nombre(), request.particiones(), request.factorReplicacion())
                .configs(request.configsOVacio());
        esperar(admin.createTopics(List.of(nuevo)).all(), "crear el tópico " + request.nombre());
        log.info("[ADMIN] Tópico creado {} particiones={} RF={} configs={}", request.nombre(),
                request.particiones(), request.factorReplicacion(), request.configsOVacio());
        return cuandoEsteDisponible(request.nombre(), request.particiones());
    }

    public void eliminarTopico(String nombre) {
        if (props.esProtegido(nombre)) {
            throw new OperacionNoPermitida("El tópico " + nombre
                    + " es del sistema Pedidos360 (o un DLT/interno de Kafka): no se puede eliminar");
        }
        describir(nombre); // 404 si no existe
        esperar(admin.deleteTopics(List.of(nombre)).all(), "eliminar el tópico " + nombre);
        log.info("[ADMIN] Tópico eliminado {}", nombre);
    }

    public TopicoDetalle aumentarParticiones(String nombre, int total) {
        int actuales = describir(nombre).partitions().size();
        if (total <= actuales) {
            throw new ConfiguracionInvalida("El tópico ya tiene " + actuales
                    + " particiones. Kafka solo permite aumentarlas (reducirlas rompería el orden por key)");
        }
        if (total > props.maxParticiones()) {
            throw new ConfiguracionInvalida("total: máximo " + props.maxParticiones());
        }
        esperar(admin.createPartitions(Map.of(nombre, NewPartitions.increaseTo(total))).all(),
                "aumentar particiones de " + nombre);
        log.info("[ADMIN] Tópico {} pasa de {} a {} particiones", nombre, actuales, total);
        return cuandoEsteDisponible(nombre, total);
    }

    // ================================================================ Configuraciones de tópico

    public List<ConfigResponse> configsDe(String nombre) {
        ConfigResource recurso = new ConfigResource(ConfigResource.Type.TOPIC, nombre);
        Map<ConfigResource, Config> configs = esperar(admin.describeConfigs(List.of(recurso)).all(),
                "leer configuraciones de " + nombre);
        return configs.get(recurso).entries().stream()
                .sorted(Comparator.comparing(ConfigEntry::name))
                .map(e -> new ConfigResponse(e.name(), e.isSensitive() ? "****" : e.value(), e.source().name(),
                        e.source() == ConfigEntry.ConfigSource.DEFAULT_CONFIG
                                || e.source() == ConfigEntry.ConfigSource.STATIC_BROKER_CONFIG
                                || e.source() == ConfigEntry.ConfigSource.DYNAMIC_DEFAULT_BROKER_CONFIG,
                        e.isReadOnly(), props.configsEditables().contains(e.name())))
                .toList();
    }

    public List<ConfigResponse> actualizarConfigs(String nombre, ActualizarConfigsRequest request) {
        TopicDescription descripcion = describir(nombre);
        validador.validar(request.configs(), factorReplicacion(descripcion));

        ConfigResource recurso = new ConfigResource(ConfigResource.Type.TOPIC, nombre);
        List<AlterConfigOp> cambios = request.configs().entrySet().stream()
                .map(e -> new AlterConfigOp(new ConfigEntry(e.getKey(), e.getValue().replace(" ", "")),
                        AlterConfigOp.OpType.SET))
                .toList();
        esperar(admin.incrementalAlterConfigs(Map.of(recurso, cambios)).all(), "modificar configuraciones de " + nombre);
        log.info("[ADMIN] Tópico {} configuraciones actualizadas: {}", nombre, request.configs());
        return configsDe(nombre);
    }

    /** Vuelve una configuración a su valor por defecto (el del broker). */
    public List<ConfigResponse> restablecerConfig(String nombre, String clave) {
        describir(nombre);
        if (!props.configsEditables().contains(clave)) {
            throw new ConfiguracionInvalida(clave + ": no es editable desde la API");
        }
        ConfigResource recurso = new ConfigResource(ConfigResource.Type.TOPIC, nombre);
        AlterConfigOp borrar = new AlterConfigOp(new ConfigEntry(clave, ""), AlterConfigOp.OpType.DELETE);
        esperar(admin.incrementalAlterConfigs(Map.of(recurso, List.of(borrar))).all(), "restablecer " + clave);
        log.info("[ADMIN] Tópico {} configuración {} restablecida al valor por defecto", nombre, clave);
        return configsDe(nombre);
    }

    // ================================================================ Consumer groups

    public List<GrupoResumen> listarGrupos() {
        var listados = esperar(admin.listConsumerGroups().all(), "listar consumer groups");
        if (listados.isEmpty()) return List.of();
        List<String> ids = listados.stream().map(ConsumerGroupListing::groupId).toList();
        Map<String, ConsumerGroupDescription> descripciones = esperar(admin.describeConsumerGroups(ids).all(),
                "describir consumer groups");
        return listados.stream()
                .sorted(Comparator.comparing(ConsumerGroupListing::groupId))
                .map(l -> {
                    ConsumerGroupDescription d = descripciones.get(l.groupId());
                    return new GrupoResumen(l.groupId(), d == null ? "DESCONOCIDO" : d.state().name(),
                            d == null ? 0 : d.members().size(), l.isSimpleConsumerGroup());
                })
                .toList();
    }

    public GrupoDetalle obtenerGrupo(String groupId) {
        ConsumerGroupDescription d = esperar(admin.describeConsumerGroups(List.of(groupId)).all(),
                "describir el grupo " + groupId).get(groupId);
        Map<TopicPartition, OffsetAndMetadata> confirmados = esperar(
                admin.listConsumerGroupOffsets(groupId).partitionsToOffsetAndMetadata(), "leer offsets de " + groupId);
        if (d == null || (d.state() == ConsumerGroupState.DEAD && confirmados.isEmpty())) {
            throw new RecursoNoEncontrado("Consumer group no encontrado: " + groupId);
        }

        List<OffsetResponse> offsets = calcularLag(confirmados);
        List<MiembroResponse> miembros = d.members().stream()
                .map(m -> new MiembroResponse(m.consumerId(), m.clientId(), m.host(),
                        m.assignment().topicPartitions().stream()
                                .sorted(Comparator.comparing(TopicPartition::topic).thenComparing(TopicPartition::partition))
                                .map(TopicPartition::toString).toList()))
                .toList();
        Map<String, Long> lagPorTopico = offsets.stream()
                .collect(Collectors.groupingBy(OffsetResponse::topico, TreeMap::new,
                        Collectors.summingLong(OffsetResponse::lag)));

        return new GrupoDetalle(groupId, d.state().name(),
                d.coordinator() == null ? null : d.coordinator().idString(), d.partitionAssignor(), miembros, offsets,
                offsets.stream().mapToLong(OffsetResponse::lag).sum(), lagPorTopico);
    }

    /**
     * Mueve los offsets del grupo en un tópico. Kafka exige que el grupo esté vacío (sin consumidores conectados):
     * con consumidores activos sus confirmaciones pisarían el cambio.
     */
    public ResetOffsetsResponse resetearOffsets(String groupId, ResetOffsetsRequest request) {
        exigirGrupoVacio(groupId, "resetear offsets");
        List<TopicPartition> particiones = describir(request.topico()).partitions().stream()
                .map(p -> new TopicPartition(request.topico(), p.partition())).toList();

        OffsetSpec destino = switch (request.a()) {
            case EARLIEST -> OffsetSpec.earliest();
            case LATEST -> OffsetSpec.latest();
            case TIMESTAMP -> OffsetSpec.forTimestamp(request.timestamp().toEpochMilli());
        };
        Map<TopicPartition, Long> nuevos = offsets(particiones, destino);
        // forTimestamp devuelve -1 si no hay mensajes posteriores: en ese caso se va al final
        Map<TopicPartition, Long> fin = offsets(particiones, OffsetSpec.latest());
        Map<TopicPartition, OffsetAndMetadata> cambios = particiones.stream().collect(Collectors.toMap(
                Function.identity(),
                tp -> new OffsetAndMetadata(nuevos.getOrDefault(tp, -1L) < 0 ? fin.get(tp) : nuevos.get(tp))));

        esperar(admin.alterConsumerGroupOffsets(groupId, cambios).all(), "resetear offsets de " + groupId);
        log.info("[ADMIN] Offsets del grupo {} en {} movidos a {}", groupId, request.topico(), request.a());
        return new ResetOffsetsResponse(groupId, request.topico(), request.a(), calcularLag(cambios));
    }

    public void eliminarGrupo(String groupId) {
        exigirGrupoVacio(groupId, "eliminar el grupo");
        esperar(admin.deleteConsumerGroups(List.of(groupId)).all(), "eliminar el grupo " + groupId);
        log.info("[ADMIN] Consumer group eliminado {}", groupId);
    }

    // ================================================================ Apoyo

    /**
     * El controlador confirma la creación antes de que TODOS los brokers conozcan el tópico (la metadata se
     * propaga en milisegundos). Se reintenta la lectura un momento en vez de responder un 404 falso.
     */
    private TopicoDetalle cuandoEsteDisponible(String nombre, int particionesEsperadas) {
        RuntimeException ultimo = null;
        for (int intento = 0; intento < 25; intento++) {
            try {
                TopicoDetalle detalle = obtenerTopico(nombre);
                if (detalle.particiones() >= particionesEsperadas) {
                    return detalle;
                }
            } catch (RecursoNoEncontrado e) {
                ultimo = e;
            }
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        if (ultimo != null) throw ultimo;
        return obtenerTopico(nombre);
    }

    private void exigirGrupoVacio(String groupId, String accion) {
        GrupoDetalle grupo = obtenerGrupo(groupId);
        if (!grupo.miembros().isEmpty()) {
            throw new OperacionNoPermitida("No se puede " + accion + ": el grupo " + groupId + " tiene "
                    + grupo.miembros().size() + " consumidor(es) activo(s). Detén el microservicio primero.");
        }
    }

    private List<OffsetResponse> calcularLag(Map<TopicPartition, OffsetAndMetadata> confirmados) {
        Map<TopicPartition, Long> fin = offsets(new ArrayList<>(confirmados.keySet()), OffsetSpec.latest());
        return confirmados.entrySet().stream()
                .filter(e -> e.getValue() != null)
                .sorted(Map.Entry.comparingByKey(
                        Comparator.comparing(TopicPartition::topic).thenComparing(TopicPartition::partition)))
                .map(e -> {
                    long ultimo = fin.getOrDefault(e.getKey(), e.getValue().offset());
                    return new OffsetResponse(e.getKey().topic(), e.getKey().partition(), e.getValue().offset(),
                            ultimo, Math.max(0, ultimo - e.getValue().offset()));
                })
                .toList();
    }

    private TopicDescription describir(String nombre) {
        return esperar(admin.describeTopics(List.of(nombre)).allTopicNames(), "describir el tópico " + nombre)
                .get(nombre);
    }

    private Map<TopicPartition, Long> offsets(List<TopicPartition> particiones, OffsetSpec spec) {
        if (particiones.isEmpty()) return Map.of();
        Map<TopicPartition, ListOffsetsResultInfo> resultado = esperar(admin.listOffsets(
                particiones.stream().collect(Collectors.toMap(Function.identity(), tp -> spec))).all(), "leer offsets");
        return resultado.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().offset()));
    }

    private static int factorReplicacion(TopicDescription d) {
        return d.partitions().isEmpty() ? 0 : d.partitions().get(0).replicas().size();
    }

    private static List<Integer> ids(List<Node> nodos) {
        return nodos.stream().map(Node::id).sorted().toList();
    }

    /** Espera el resultado con timeout y traduce los errores de Kafka a excepciones de la API. */
    private <T> T esperar(KafkaFuture<T> futuro, String accion) {
        try {
            return futuro.get(props.timeoutMs(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ClusterNoDisponible("Operación interrumpida al " + accion, e);
        } catch (TimeoutException e) {
            throw new ClusterNoDisponible("El clúster Kafka no respondió a tiempo al " + accion, e);
        } catch (ExecutionException e) {
            Throwable causa = e.getCause();
            if (causa instanceof UnknownTopicOrPartitionException) {
                throw new RecursoNoEncontrado("Tópico no encontrado (" + accion + ")");
            }
            if (causa instanceof GroupIdNotFoundException) {
                throw new RecursoNoEncontrado("Consumer group no encontrado (" + accion + ")");
            }
            if (causa instanceof TopicExistsException) {
                throw new RecursoYaExiste("Ya existe un tópico con ese nombre");
            }
            if (causa instanceof GroupNotEmptyException) {
                throw new OperacionNoPermitida("El grupo tiene consumidores activos");
            }
            if (causa instanceof InvalidPartitionsException || causa instanceof InvalidReplicationFactorException
                    || causa instanceof InvalidConfigurationException || causa instanceof InvalidRequestException
                    || causa instanceof PolicyViolationException) {
                throw new ConfiguracionInvalida("Kafka rechazó la operación: " + causa.getMessage());
            }
            if (causa instanceof org.apache.kafka.common.errors.TimeoutException) {
                throw new ClusterNoDisponible("El clúster Kafka no respondió a tiempo al " + accion, causa);
            }
            throw new ClusterNoDisponible("Error de Kafka al " + accion + ": " + causa.getMessage(), causa);
        }
    }
}
