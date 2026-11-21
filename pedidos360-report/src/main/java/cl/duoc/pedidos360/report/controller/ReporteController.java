package cl.duoc.pedidos360.report.controller;

import cl.duoc.pedidos360.report.service.Rango;
import cl.duoc.pedidos360.report.service.ReporteService;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** API de solo lectura del panel de operaciones. Todo exige JWT válido de Entra ID. */
@RestController
@RequestMapping("/api/report")
public class ReporteController {

    private final ReporteService reporteService;

    public ReporteController(ReporteService reporteService) {
        this.reporteService = reporteService;
    }

    @Operation(summary = "KPIs del panel: ventas, ticket promedio, lead time, estados activos y ventas por hora")
    @GetMapping("/kpis")
    public ReporteService.Kpis kpis(@RequestParam(defaultValue = "last24h") String range) {
        return reporteService.kpis(Rango.desde(range));
    }

    @Operation(summary = "Ventas (cantidad y monto) agrupadas por hora")
    @GetMapping("/ventas-por-hora")
    public List<ReporteService.VentaHora> ventasPorHora(@RequestParam(defaultValue = "last24h") String range) {
        return reporteService.ventasPorHora(Rango.desde(range));
    }

    @Operation(summary = "Productos más vendidos (unidades) en el rango")
    @GetMapping("/top-products")
    public List<ReporteService.TopProducto> topProductos(@RequestParam(defaultValue = "last7d") String range,
                                                         @RequestParam(defaultValue = "5") int limit) {
        return reporteService.topProductos(Rango.desde(range), limit);
    }

    @Operation(summary = "Pedidos en curso por estado")
    @GetMapping("/estados-activos")
    public Map<String, Long> estadosActivos() {
        return reporteService.estadosActivos();
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail parametroInvalido(IllegalArgumentException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
    }
}
