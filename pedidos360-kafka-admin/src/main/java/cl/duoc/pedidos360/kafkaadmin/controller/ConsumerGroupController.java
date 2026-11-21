package cl.duoc.pedidos360.kafkaadmin.controller;

import cl.duoc.pedidos360.kafkaadmin.dto.Dtos;
import cl.duoc.pedidos360.kafkaadmin.dto.Dtos.GrupoDetalle;
import cl.duoc.pedidos360.kafkaadmin.dto.Dtos.GrupoResumen;
import cl.duoc.pedidos360.kafkaadmin.dto.Dtos.ResetOffsetsRequest;
import cl.duoc.pedidos360.kafkaadmin.dto.Dtos.ResetOffsetsResponse;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Recurso "consumer groups": quién consume, qué particiones tiene cada miembro, offsets y lag. */
@Tag(name = "Consumer groups")
@RestController
@RequestMapping("/api/kafka-admin/consumer-groups")
public class ConsumerGroupController {

    private final KafkaAdminService adminService;

    public ConsumerGroupController(KafkaAdminService adminService) {
        this.adminService = adminService;
    }

    @Operation(summary = "Lista los consumer groups con su estado y cantidad de miembros")
    @GetMapping
    public List<GrupoResumen> listar() {
        return adminService.listarGrupos();
    }

    @Operation(summary = "Detalle: miembros y sus particiones, offset confirmado, offset final y lag por partición")
    @GetMapping("/{groupId}")
    public GrupoDetalle obtener(@PathVariable @Pattern(regexp = Dtos.PATRON_GRUPO, message = Dtos.MENSAJE_GRUPO)
                                String groupId) {
        return adminService.obtenerGrupo(groupId);
    }

    @Operation(summary = "Mueve los offsets del grupo (EARLIEST = reprocesar todo, LATEST = saltar lo pendiente)."
            + " El grupo debe estar sin consumidores activos")
    @PostMapping("/{groupId}/reset-offsets")
    public ResetOffsetsResponse resetearOffsets(
            @PathVariable @Pattern(regexp = Dtos.PATRON_GRUPO, message = Dtos.MENSAJE_GRUPO) String groupId,
            @Valid @RequestBody ResetOffsetsRequest request) {
        return adminService.resetearOffsets(groupId, request);
    }

    @Operation(summary = "Elimina un grupo sin consumidores activos (204)")
    @DeleteMapping("/{groupId}")
    public ResponseEntity<Void> eliminar(@PathVariable @Pattern(regexp = Dtos.PATRON_GRUPO,
            message = Dtos.MENSAJE_GRUPO) String groupId) {
        adminService.eliminarGrupo(groupId);
        return ResponseEntity.noContent().build();
    }
}
