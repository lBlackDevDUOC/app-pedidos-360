package cl.duoc.pedidos360.audit.controller;

import cl.duoc.pedidos360.audit.model.RegistroAuditoria;
import cl.duoc.pedidos360.audit.service.AuditoriaService;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

/** API de solo lectura (solo GET): la auditoría no se crea ni se modifica desde afuera, solo llega por Kafka. */
@RestController
@RequestMapping("/api/audit")
public class AuditoriaController {

    private final AuditoriaService auditoriaService;

    public AuditoriaController(AuditoriaService auditoriaService) {
        this.auditoriaService = auditoriaService;
    }

    @Operation(summary = "Busca en la línea de tiempo. Filtros opcionales: usuario, tipo (acción), entidadId, rango de fechas")
    @GetMapping
    public Page<RegistroAuditoria> buscar(
            @RequestParam(required = false) String usuario,
            @RequestParam(required = false) String tipo,
            @RequestParam(required = false) String entidadId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant hasta,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return auditoriaService.buscar(usuario, tipo, entidadId, desde, hasta, page, size);
    }

    @Operation(summary = "Trazabilidad completa de un pedido, en orden cronológico")
    @GetMapping("/pedidos/{id}")
    public List<RegistroAuditoria> timelinePedido(@PathVariable Long id) {
        return auditoriaService.timelinePedido(id);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail parametroInvalido(IllegalArgumentException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
    }
}
