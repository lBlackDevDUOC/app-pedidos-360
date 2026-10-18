package cl.duoc.pedidos360.admin.controller;

import cl.duoc.pedidos360.admin.dto.ColaResponse;
import cl.duoc.pedidos360.admin.dto.CrearColaRequest;
import cl.duoc.pedidos360.admin.dto.PurgaResponse;
import cl.duoc.pedidos360.admin.dto.ReglasNombres;
import cl.duoc.pedidos360.admin.service.RabbitAdminService;
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
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.util.List;

/** Recurso "colas". Solo traduce HTTP ↔ servicio: no contiene lógica de RabbitMQ. */
@Tag(name = "Colas")
@RestController
@RequestMapping("/api/admin/queues")
public class ColaController {

    private final RabbitAdminService adminService;

    public ColaController(RabbitAdminService adminService) {
        this.adminService = adminService;
    }

    @Operation(summary = "Lista las colas con sus métricas (listos, sin confirmar, consumidores)")
    @GetMapping
    public List<ColaResponse> listar() {
        return adminService.listarColas();
    }

    @Operation(summary = "Detalle de una cola")
    @GetMapping("/{nombre}")
    public ColaResponse obtener(
            @PathVariable @Pattern(regexp = ReglasNombres.PATRON_NOMBRE, message = ReglasNombres.MENSAJE_NOMBRE)
            String nombre) {
        return adminService.obtenerCola(nombre);
    }

    @Operation(summary = "Crea una cola (201). 409 si ya existe o es una cola protegida del sistema")
    @PostMapping
    public ResponseEntity<ColaResponse> crear(@Valid @RequestBody CrearColaRequest request) {
        ColaResponse creada = adminService.crearCola(request);
        return ResponseEntity
                .created(ServletUriComponentsBuilder.fromCurrentRequest().path("/{nombre}").buildAndExpand(creada.nombre()).toUri())
                .body(creada);
    }

    @Operation(summary = "Elimina una cola (204). 404 si no existe")
    @DeleteMapping("/{nombre}")
    public ResponseEntity<Void> eliminar(
            @PathVariable @Pattern(regexp = ReglasNombres.PATRON_NOMBRE, message = ReglasNombres.MENSAJE_NOMBRE)
            String nombre) {
        adminService.eliminarCola(nombre);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Purga: elimina todos los mensajes de la cola, sin borrar la cola")
    @DeleteMapping("/{nombre}/messages")
    public PurgaResponse purgar(
            @PathVariable @Pattern(regexp = ReglasNombres.PATRON_NOMBRE, message = ReglasNombres.MENSAJE_NOMBRE)
            String nombre) {
        return adminService.purgarCola(nombre);
    }
}
