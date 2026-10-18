package cl.duoc.pedidos360.admin.controller;

import cl.duoc.pedidos360.admin.dto.CrearExchangeRequest;
import cl.duoc.pedidos360.admin.dto.ExchangeResponse;
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

/** Recurso "exchanges". */
@Tag(name = "Exchanges")
@RestController
@RequestMapping("/api/admin/exchanges")
public class ExchangeController {

    private final RabbitAdminService adminService;

    public ExchangeController(RabbitAdminService adminService) {
        this.adminService = adminService;
    }

    @Operation(summary = "Lista los exchanges (sin los internos amq.* del broker)")
    @GetMapping
    public List<ExchangeResponse> listar() {
        return adminService.listarExchanges();
    }

    @Operation(summary = "Crea un exchange DIRECT, TOPIC, FANOUT o HEADERS (201)")
    @PostMapping
    public ResponseEntity<ExchangeResponse> crear(@Valid @RequestBody CrearExchangeRequest request) {
        ExchangeResponse creado = adminService.crearExchange(request);
        return ResponseEntity
                .created(ServletUriComponentsBuilder.fromCurrentRequest().path("/{nombre}").buildAndExpand(creado.nombre()).toUri())
                .body(creado);
    }

    @Operation(summary = "Elimina un exchange (204). 404 si no existe")
    @DeleteMapping("/{nombre}")
    public ResponseEntity<Void> eliminar(
            @PathVariable @Pattern(regexp = ReglasNombres.PATRON_NOMBRE, message = ReglasNombres.MENSAJE_NOMBRE)
            String nombre) {
        adminService.eliminarExchange(nombre);
        return ResponseEntity.noContent().build();
    }
}
