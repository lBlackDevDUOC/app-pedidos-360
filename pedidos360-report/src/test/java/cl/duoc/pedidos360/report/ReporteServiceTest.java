package cl.duoc.pedidos360.report;

import cl.duoc.pedidos360.report.messaging.event.EventoPedido;
import cl.duoc.pedidos360.report.repository.EventoProcesadoRepository;
import cl.duoc.pedidos360.report.repository.ItemVendidoRepository;
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
import java.util.List;
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
    @Autowired
    private ItemVendidoRepository items;

    private ReporteService service;

    @BeforeEach
    void preparar() {
        Clock reloj = Clock.fixed(AHORA.atZone(ZONA).toInstant(), ZONA);
        service = new ReporteService(proyecciones, procesados, items, reloj);
    }

    private EventoPedido evento(long pedidoId, String estado, double total, LocalDateTime creado,
                                LocalDateTime entregado, Instant ocurrido) {
        return new EventoPedido(UUID.randomUUID().toString(), "Order" + estado, 1, ocurrido, "ms-pedidos", "t", "c",
                pedidoId, "Cliente", total, null, estado, creado, entregado, null);
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
    void rankingDeProductosIgnoraPedidosCancelados() {
        Instant t = AHORA.atZone(ZONA).toInstant();
        var pan = new EventoPedido.Item(1L, "Pan", 3, 1000.0);
        var cafe = new EventoPedido.Item(2L, "Café", 1, 2000.0);
        var panCancelado = new EventoPedido.Item(1L, "Pan", 1, 1000.0);
        service.registrar(new EventoPedido("a", "OrderCreated", 1, t, "ms", "t", "c", 10L, "C", 5000.0, null,
                "CREADO", AHORA.minusHours(1), null, List.of(pan, cafe)));
        service.registrar(new EventoPedido("b", "OrderCreated", 1, t, "ms", "t", "c", 11L, "C", 1000.0, null,
                "CREADO", AHORA.minusHours(1), null, List.of(panCancelado)));
        service.registrar(new EventoPedido("c", "OrderCancelled", 1, t.plusSeconds(5), "ms", "t", "c", 11L, "C",
                1000.0, "CREADO", "CANCELADO", AHORA.minusHours(1), null, List.of(panCancelado)));

        var top = service.topProductos(Rango.LAST_7D, 5);

        assertThat(top).extracting(ReporteService.TopProducto::nombre).containsExactly("Pan", "Café");
        assertThat(top.get(0).unidades()).isEqualTo(3);
        assertThat(top.get(0).monto()).isEqualTo(3000.0);
    }

    @Test
    void rangoInvalidoSeRechaza() {
        assertThatThrownBy(() -> Rango.desde("ayer")).isInstanceOf(IllegalArgumentException.class);
        assertThat(Rango.desde("LAST7D")).isEqualTo(Rango.LAST_7D);
    }
}
