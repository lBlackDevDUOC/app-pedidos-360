package cl.duoc.pedidos360.report.service;

import cl.duoc.pedidos360.report.messaging.event.EventoPedido;
import cl.duoc.pedidos360.report.model.EventoProcesado;
import cl.duoc.pedidos360.report.model.PedidoProyeccion;
import cl.duoc.pedidos360.report.repository.EventoProcesadoRepository;
import cl.duoc.pedidos360.report.repository.PedidoProyeccionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * Lógica de reportería. No conoce Kafka: recibe un evento ya validado y actualiza la proyección; los KPIs se
 * calculan sobre esa proyección. Así la API de reportes responde en milisegundos sin tocar ms-pedidos.
 */
@Service
public class ReporteService {

    private static final Logger log = LoggerFactory.getLogger(ReporteService.class);

    private final PedidoProyeccionRepository proyecciones;
    private final EventoProcesadoRepository procesados;
    private final Clock reloj;

    public ReporteService(PedidoProyeccionRepository proyecciones, EventoProcesadoRepository procesados, Clock reloj) {
        this.proyecciones = proyecciones;
        this.procesados = procesados;
        this.reloj = reloj;
    }

    /**
     * Aplica un evento a la proyección del pedido. Es idempotente (eventoId) y tolera eventos fuera de orden
     * (si llega uno más antiguo que el último aplicado, se registra pero no pisa el estado actual).
     *
     * @return true si cambió la proyección
     */
    @Transactional
    public boolean registrar(EventoPedido evento) {
        if (procesados.existsById(evento.eventoId())) {
            log.info("[DUPLICADO] eventoId={} ya procesado, se ignora", evento.eventoId());
            return false;
        }
        PedidoProyeccion proyeccion = proyecciones.findById(evento.pedidoId())
                .orElseGet(() -> new PedidoProyeccion(evento.pedidoId()));

        boolean aplicado = false;
        if (proyeccion.esAnteriorA(evento.ocurridoEn())) {
            log.warn("[FUERA-DE-ORDEN] pedido={} evento {} ({}) es anterior al último aplicado, no se aplica",
                    evento.pedidoId(), evento.tipo(), evento.ocurridoEn());
        } else {
            proyeccion.aplicar(evento.estado(), evento.total(), evento.clienteNombre(), evento.fechaCreacion(),
                    evento.fechaEntrega(), evento.ocurridoEn());
            proyecciones.save(proyeccion);
            aplicado = true;
        }
        procesados.save(new EventoProcesado(evento.eventoId(), evento.tipo(), evento.pedidoId()));
        return aplicado;
    }

    /** Panel principal: todo lo que muestra /reports en una sola llamada. */
    @Transactional(readOnly = true)
    public Kpis kpis(Rango rango) {
        LocalDateTime hasta = LocalDateTime.now(reloj);
        LocalDateTime desde = hasta.minus(rango.duracion());

        List<PedidoProyeccion> creados = proyecciones.findByFechaCreacionBetween(desde, hasta);
        List<PedidoProyeccion> vendidos = creados.stream().filter(p -> !"CANCELADO".equals(p.getEstado())).toList();
        double ventasTotales = vendidos.stream().mapToDouble(p -> p.getTotal() == null ? 0 : p.getTotal()).sum();

        List<PedidoProyeccion> entregados = proyecciones.findByFechaEntregaBetween(desde, hasta);
        Double leadTimePromedioMin = entregados.stream()
                .map(PedidoProyeccion::getLeadTimeSegundos).filter(Objects::nonNull)
                .mapToLong(Long::longValue).average()
                .stream().mapToObj(seg -> redondear(seg / 60.0)).findFirst().orElse(null);

        return new Kpis(
                rango.codigo(), desde, hasta,
                creados.size(),
                entregados.size(),
                creados.size() - vendidos.size(),
                redondear(ventasTotales),
                vendidos.isEmpty() ? 0 : redondear(ventasTotales / vendidos.size()),
                leadTimePromedioMin,
                estadosActivos(),
                ventasPorHora(vendidos, desde, hasta),
                proyecciones.ultimoEvento().orElse(null),
                Instant.now(reloj));
    }

    /** Pedidos en curso (no ENTREGADO ni CANCELADO) por estado, en el orden del ciclo de vida. */
    @Transactional(readOnly = true)
    public Map<String, Long> estadosActivos() {
        Map<String, Long> conteo = new LinkedHashMap<>();
        for (String estado : List.of("CREADO", "ACEPTADO", "EN_PREPARACION", "DESPACHADO")) {
            conteo.put(estado, 0L);
        }
        for (Object[] fila : proyecciones.contarPorEstadoExcepto(PedidoProyeccion.ESTADOS_FINALES)) {
            conteo.put((String) fila[0], (Long) fila[1]);
        }
        return conteo;
    }

    /** Agrupa por hora de creación. Las horas sin ventas aparecen con 0 para que el gráfico no tenga huecos. */
    @Transactional(readOnly = true)
    public List<VentaHora> ventasPorHora(Rango rango) {
        LocalDateTime hasta = LocalDateTime.now(reloj);
        LocalDateTime desde = hasta.minus(rango.duracion());
        List<PedidoProyeccion> vendidos = proyecciones.findByFechaCreacionBetween(desde, hasta).stream()
                .filter(p -> !"CANCELADO".equals(p.getEstado())).toList();
        return ventasPorHora(vendidos, desde, hasta);
    }

    private List<VentaHora> ventasPorHora(List<PedidoProyeccion> vendidos, LocalDateTime desde, LocalDateTime hasta) {
        TreeMap<LocalDateTime, double[]> porHora = new TreeMap<>();
        for (LocalDateTime h = desde.truncatedTo(ChronoUnit.HOURS); !h.isAfter(hasta); h = h.plusHours(1)) {
            porHora.put(h, new double[]{0, 0});
        }
        for (PedidoProyeccion p : vendidos) {
            double[] acumulado = porHora.computeIfAbsent(p.getFechaCreacion().truncatedTo(ChronoUnit.HOURS),
                    h -> new double[]{0, 0});
            acumulado[0]++;
            acumulado[1] += p.getTotal() == null ? 0 : p.getTotal();
        }
        return porHora.entrySet().stream()
                .map(e -> new VentaHora(e.getKey(), (long) e.getValue()[0], redondear(e.getValue()[1])))
                .toList();
    }

    private static double redondear(double valor) {
        return Math.round(valor * 100) / 100.0;
    }

    public record VentaHora(LocalDateTime hora, long pedidos, double monto) {}

    /**
     * @param leadTimePromedioMinutos promedio CREADO → ENTREGADO de los pedidos entregados en el rango
     * @param ultimoEventoEn          último evento recibido de Kafka: muestra qué tan "fresco" está el panel
     */
    public record Kpis(
            String rango, LocalDateTime desde, LocalDateTime hasta,
            long pedidosCreados, long pedidosEntregados, long pedidosCancelados,
            double ventasTotales, double ticketPromedio, Double leadTimePromedioMinutos,
            Map<String, Long> estadosActivos, List<VentaHora> ventasPorHora,
            Instant ultimoEventoEn, Instant generadoEn) {}
}
