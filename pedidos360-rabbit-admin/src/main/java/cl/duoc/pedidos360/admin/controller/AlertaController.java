package cl.duoc.pedidos360.admin.controller;

import cl.duoc.pedidos360.admin.dto.AlertaDlqResponse;
import cl.duoc.pedidos360.admin.service.DlqMonitor;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Alertas activas de DLQ (colas de cuarentena con mensajes). */
@Tag(name = "Alertas")
@RestController
@RequestMapping("/api/admin/alerts")
public class AlertaController {

    private final DlqMonitor dlqMonitor;

    public AlertaController(DlqMonitor dlqMonitor) {
        this.dlqMonitor = dlqMonitor;
    }

    @Operation(summary = "DLQ que superaron el umbral de mensajes en la última revisión")
    @GetMapping
    public List<AlertaDlqResponse> listar() {
        return dlqMonitor.alertasActivas();
    }
}
