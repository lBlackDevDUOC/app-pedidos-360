package cl.duoc.pedidos360.kafkaadmin.controller;

import cl.duoc.pedidos360.kafkaadmin.dto.Dtos;
import cl.duoc.pedidos360.kafkaadmin.dto.Dtos.ActualizarConfigsRequest;
import cl.duoc.pedidos360.kafkaadmin.dto.Dtos.AumentarParticionesRequest;
import cl.duoc.pedidos360.kafkaadmin.dto.Dtos.ConfigResponse;
import cl.duoc.pedidos360.kafkaadmin.dto.Dtos.CrearTopicoRequest;
import cl.duoc.pedidos360.kafkaadmin.dto.Dtos.TopicoDetalle;
import cl.duoc.pedidos360.kafkaadmin.dto.Dtos.TopicoResumen;
import cl.duoc.pedidos360.kafkaadmin.service.KafkaAdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.util.List;

/** Recurso "tópicos" y sus configuraciones. Solo traduce HTTP ↔ servicio: no contiene lógica de Kafka. */
@Tag(name = "Tópicos")
@RestController
@RequestMapping("/api/kafka-admin/topics")
public class TopicoController {

    private final KafkaAdminService adminService;

    public TopicoController(KafkaAdminService adminService) {
        this.adminService = adminService;
    }

    @Operation(summary = "Lista los tópicos con particiones y factor de replicación")
    @GetMapping
    public List<TopicoResumen> listar(@RequestParam(defaultValue = "false") boolean incluirInternos) {
        return adminService.listarTopicos(incluirInternos);
    }

    @Operation(summary = "Detalle: líder, réplicas e ISR por partición, offsets, mensajes y configs no por defecto")
    @GetMapping("/{nombre}")
    public TopicoDetalle obtener(@PathVariable @Pattern(regexp = Dtos.PATRON_TOPICO, message = Dtos.MENSAJE_TOPICO)
                                 String nombre) {
        return adminService.obtenerTopico(nombre);
    }

    @Operation(summary = "Crea un tópico (201). 409 si ya existe, 400 si la configuración no es válida")
    @PostMapping
    public ResponseEntity<TopicoDetalle> crear(@Valid @RequestBody CrearTopicoRequest request) {
        TopicoDetalle creado = adminService.crearTopico(request);
        return ResponseEntity.created(ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{nombre}").buildAndExpand(creado.nombre()).toUri()).body(creado);
    }

    @Operation(summary = "Elimina un tópico (204). 409 si es un tópico del sistema")
    @DeleteMapping("/{nombre}")
    public ResponseEntity<Void> eliminar(@PathVariable @Pattern(regexp = Dtos.PATRON_TOPICO,
            message = Dtos.MENSAJE_TOPICO) String nombre) {
        adminService.eliminarTopico(nombre);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Aumenta las particiones (Kafka no permite reducirlas)")
    @PostMapping("/{nombre}/partitions")
    public TopicoDetalle aumentarParticiones(
            @PathVariable @Pattern(regexp = Dtos.PATRON_TOPICO, message = Dtos.MENSAJE_TOPICO) String nombre,
            @Valid @RequestBody AumentarParticionesRequest request) {
        return adminService.aumentarParticiones(nombre, request.total());
    }

    @Operation(summary = "Todas las configuraciones del tópico, indicando origen y si son editables")
    @GetMapping("/{nombre}/configs")
    public List<ConfigResponse> configs(@PathVariable @Pattern(regexp = Dtos.PATRON_TOPICO,
            message = Dtos.MENSAJE_TOPICO) String nombre) {
        return adminService.configsDe(nombre);
    }

    @Operation(summary = "Modifica configuraciones (retention.ms, cleanup.policy, min.insync.replicas, ...)")
    @PutMapping("/{nombre}/configs")
    public List<ConfigResponse> actualizarConfigs(
            @PathVariable @Pattern(regexp = Dtos.PATRON_TOPICO, message = Dtos.MENSAJE_TOPICO) String nombre,
            @Valid @RequestBody ActualizarConfigsRequest request) {
        return adminService.actualizarConfigs(nombre, request);
    }

    @Operation(summary = "Vuelve una configuración a su valor por defecto")
    @DeleteMapping("/{nombre}/configs/{clave}")
    public List<ConfigResponse> restablecerConfig(
            @PathVariable @Pattern(regexp = Dtos.PATRON_TOPICO, message = Dtos.MENSAJE_TOPICO) String nombre,
            @PathVariable String clave) {
        return adminService.restablecerConfig(nombre, clave);
    }
}
