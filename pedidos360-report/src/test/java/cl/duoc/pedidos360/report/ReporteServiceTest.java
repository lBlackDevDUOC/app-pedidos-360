package cl.duoc.pedidos360.report;

import cl.duoc.pedidos360.report.messaging.event.EventoPedido;
import cl.duoc.pedidos360.report.repository.EventoProcesadoRepository;
import cl.duoc.pedidos360.report.repository.PedidoProyeccionRepository;
import cl.duoc.pedidos360.report.service.Rango;
import cl.duoc.pedidos360.report.service.ReporteService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Lógica de proyección y KPIs sobre una BD H2 en memoria, con la hora fijada. */
@DataJpaTest
class ReporteServiceTest {

    private static final ZoneId ZONA = ZoneId.of("America/Santiago");
    private static final LocalDateTime AHORA = LocalDateTime.of(2026, 11, 20, 15, 30);

    @Autowired
    private PedidoProyeccionRepository proyecciones;
    @Autowired
    private EventoProcesadoRepository procesados;

    private ReporteService service;

    @BeforeEach
    void preparar() {
        Clock reloj = Clock.fixed(AHORA.atZone(ZONA).toInstant(), ZONA);
        service = new ReporteService(proyecciones, procesados, reloj);
    }

    private EventoPedido evento(long pedidoId, String estado, double total, LocalDateTime creado,
                                LocalDateTime entregado, Instant ocurrido) {
        return new EventoPedido(UUID.randomUUID().toString(), "Order" + estado, 1, ocurrido, "ms-pedidos", "t", "c",
                pedidoId, "Cliente", total, null, estado, creado, entregado);
    }

    @Test
    void calculaVentasLeadTimeYEstadosActivos() {
        LocalDateTime creado = AHORA.minusHours(2);
        Instant t0 = AHORA.minusHours(2).atZone(ZONA).toInstant();
        service.registrar(evento(1, "CREADO", 10000, creado, null, t0));
        service.registrar(evento(1, "ENTREGADO", 10000, creado, creado.plusMinutes(45), t0.plusSeconds(2700)));
        service.registrar(evento(2, "ACEPTADO", 5000, AHORA.minusMinutes(10), null, t0.plusSeconds(6000)));
        service.registrar(evento(3, "CANCELADO", 7000, AHORA.minusMinutes(5), null, t0.plusSeconds(6600)));

        ReporteService.Kpis kpis = service.kpis(Rango.LAST_24H);

        assertThat(kpis.pedidosCreados()).isEqualTo(3);
        assertThat(kpis.pedidosEntregados()).isEqualTo(1);
        assertThat(kpis.pedidosCancelados()).isEqualTo(1);
        assertThat(kpis.ventasTotales()).isEqualTo(15000);       // el cancelado no suma
        assertThat(kpis.ticketPromedio()).isEqualTo(7500);
        assertThat(kpis.leadTimePromedioMinutos()).isEqualTo(45.0);
        assertThat(kpis.estadosActivos()).containsEntry("ACEPTADO", 1L).containsEntry("CREADO", 0L);
        assertThat(kpis.ventasPorHora()).anySatisfy(v -> {
            assertThat(v.hora()).isEqualTo(AHORA.minusHours(2).withMinute(0));
            assertThat(v.monto()).isEqualTo(10000);
        });
    }

    @Test
    void eventoDuplicadoNoSeAplicaDosVeces() {
        EventoPedido creado = evento(5, "CREADO", 3000, AHORA.minusMinutes(1), null, Instant.now());

        assertThat(service.registrar(creado)).isTrue();
        assertThat(service.registrar(creado)).isFalse();
        assertThat(procesados.count()).isEqualTo(1);
    }

    @Test
    void eventoAtrasadoNoPisaElEstadoMasReciente() {
        Instant t = AHORA.atZone(ZONA).toInstant();
        service.registrar(evento(6, "ACEPTADO", 3000, AHORA.minusMinutes(5), null, t));
        service.registrar(evento(6, "CREADO", 3000, AHORA.minusMinutes(5), null, t.minusSeconds(60)));

        assertThat(proyecciones.findById(6L)).get().extracting("estado").isEqualTo("ACEPTADO");
    }

    @Test
    void rangoInvalidoSeRechaza() {
        assertThatThrownBy(() -> Rango.desde("ayer")).isInstanceOf(IllegalArgumentException.class);
        assertThat(Rango.desde("LAST7D")).isEqualTo(Rango.LAST_7D);
    }
}
