package cl.duoc.pedidos360.admin.controller;

import cl.duoc.pedidos360.admin.dto.BindingResponse;
import cl.duoc.pedidos360.admin.dto.CrearBindingRequest;
import cl.duoc.pedidos360.admin.dto.ReglasNombres;
import cl.duoc.pedidos360.admin.service.RabbitAdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Recurso "bindings". Un binding no tiene un id propio: se identifica por (exchange, cola, routingKey),
 * por eso el DELETE recibe esos tres valores como parámetros de consulta.
 */
@Tag(name = "Bindings")
@RestController
@RequestMapping("/api/admin/bindings")
public class BindingController {

    private final RabbitAdminService adminService;

    public BindingController(RabbitAdminService adminService) {
        this.adminService = adminService;
    }

    @Operation(summary = "Lista los bindings exchange → cola")
    @GetMapping
    public List<BindingResponse> listar() {
        return adminService.listarBindings();
    }

    @Operation(summary = "Enlaza una cola a un exchange (201). 404 si la cola o el exchange no existen")
    @PostMapping
    public ResponseEntity<BindingResponse> crear(@Valid @RequestBody CrearBindingRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(adminService.crearBinding(request));
    }

    @Operation(summary = "Elimina un binding (204). 404 si no existe")
    @DeleteMapping
    public ResponseEntity<Void> eliminar(
            @RequestParam @NotBlank(message = "es obligatorio")
            @Pattern(regexp = ReglasNombres.PATRON_NOMBRE, message = ReglasNombres.MENSAJE_NOMBRE) String exchange,
            @RequestParam @NotBlank(message = "es obligatorio")
            @Pattern(regexp = ReglasNombres.PATRON_NOMBRE, message = ReglasNombres.MENSAJE_NOMBRE) String cola,
            @RequestParam(defaultValue = "")
            @Pattern(regexp = ReglasNombres.PATRON_ROUTING_KEY, message = ReglasNombres.MENSAJE_ROUTING_KEY) String routingKey) {
        adminService.eliminarBinding(exchange, cola, routingKey);
        return ResponseEntity.noContent().build();
    }
}
