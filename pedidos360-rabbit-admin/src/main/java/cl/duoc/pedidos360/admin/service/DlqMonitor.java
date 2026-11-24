package cl.duoc.pedidos360.admin.service;

import cl.duoc.pedidos360.admin.config.AdminProperties;
import cl.duoc.pedidos360.admin.dto.AlertaDlqResponse;
import cl.duoc.pedidos360.admin.dto.ColaResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Alertas de DLQ: cada cierto intervalo revisa las colas de cuarentena y deja un WARN en el log cuando
 * acumulan mensajes. Una DLQ con mensajes significa que algo falla de forma persistente y alguien debe revisarlo.
 * Las DLQ no tienen consumidor automático a propósito: los mensajes quedan disponibles para inspección.
 */
@Component
public class DlqMonitor {

    private static final Logger log = LoggerFactory.getLogger(DlqMonitor.class);

    private final RabbitAdminService adminService;
    private final AdminProperties props;
    private final Map<String, AlertaDlqResponse> alertasActivas = new ConcurrentHashMap<>();

    public DlqMonitor(RabbitAdminService adminService, AdminProperties props) {
        this.adminService = adminService;
        this.props = props;
    }

    @Scheduled(fixedDelayString = "${app.admin.alertas.intervalo-ms}", initialDelay = 10_000)
    public void revisar() {
        List<ColaResponse> dlqs;
        try {
            dlqs = adminService.listarColas().stream().filter(ColaResponse::esDlq).toList();
        } catch (RuntimeException e) {
            log.warn("[ALERTA-DLQ] No se pudo consultar RabbitMQ: {}", e.getMessage());
            return;
        }

        int umbral = props.alertas().umbralMensajes();
        Set<String> conProblemas = new HashSet<>();
        for (ColaResponse dlq : dlqs) {
            if (dlq.mensajes() >= umbral) {
                conProblemas.add(dlq.nombre());
                AlertaDlqResponse previa = alertasActivas.get(dlq.nombre());
                Instant desde = previa != null ? previa.detectadaEn() : Instant.now();
                alertasActivas.put(dlq.nombre(), new AlertaDlqResponse(dlq.nombre(), dlq.mensajes(), umbral, desde));
                log.warn("[ALERTA-DLQ] {} tiene {} mensaje(s) en cuarentena (umbral {}). Revisar logs del consumidor.",
                        dlq.nombre(), dlq.mensajes(), umbral);
            }
        }
        alertasActivas.keySet().removeIf(nombre -> {
            boolean resuelta = !conProblemas.contains(nombre);
            if (resuelta) {
                log.info("[ALERTA-DLQ] {} volvió a estar bajo el umbral", nombre);
            }
            return resuelta;
        });
    }

    public List<AlertaDlqResponse> alertasActivas() {
        return alertasActivas.values().stream()
                .sorted(Comparator.comparing(AlertaDlqResponse::cola))
                .toList();
    }
}
