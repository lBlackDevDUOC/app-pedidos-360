package cl.duoc.pedidos360.documentos.service;

import cl.duoc.pedidos360.documentos.config.SimulacionProperties;
import cl.duoc.pedidos360.documentos.model.Comprobante;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ComprobanteServiceTest {

    private final ComprobanteService service = new ComprobanteService(
            new SimuladorFallos(new SimulacionProperties(true, 7, 13, 666)));

    @Test
    void emite_calculaNetoEIvaYNumeroDerivadoDelPedido() {
        Comprobante c = service.emitir(12L, "Ana", "ana@duoc.cl", 11900.0);

        assertThat(c.numero()).isEqualTo("BOL-00000012");
        assertThat(c.neto()).isEqualTo(10000.0);
        assertThat(c.iva()).isEqualTo(1900.0);
    }

    @Test
    void mismoPedido_mismoNumero_esIdempotente() {
        assertThat(service.emitir(12L, "Ana", "a@b.cl", 1000.0).numero())
                .isEqualTo(service.emitir(12L, "Ana", "a@b.cl", 1000.0).numero());
    }

    @Test
    void totalInvalido_esNoRecuperable() {
        assertThatThrownBy(() -> service.emitir(1L, "Ana", "a@b.cl", 0.0))
                .isInstanceOf(DatosPedidoInvalidosException.class);
    }

    @Test
    void simulacion_fallaUnaVezYLuegoSeRecupera() {
        assertThatThrownBy(() -> service.emitir(1L, "Ana", "a@b.cl", 7.0))
                .isInstanceOf(ServicioNoDisponibleException.class);
        assertThat(service.emitir(1L, "Ana", "a@b.cl", 7.0).numero()).isEqualTo("BOL-00000001");
    }

    @Test
    void simulacion_fallaSiempreYDatosInvalidos() {
        assertThatThrownBy(() -> service.emitir(2L, "Ana", "a@b.cl", 13.0))
                .isInstanceOf(ServicioNoDisponibleException.class);
        assertThatThrownBy(() -> service.emitir(2L, "Ana", "a@b.cl", 13.0))
                .isInstanceOf(ServicioNoDisponibleException.class);
        assertThatThrownBy(() -> service.emitir(3L, "Ana", "a@b.cl", 666.0))
                .isInstanceOf(DatosPedidoInvalidosException.class);
    }

    @Test
    void simulacionDeshabilitada_noFalla() {
        ComprobanteService sinSimulacion = new ComprobanteService(
                new SimuladorFallos(new SimulacionProperties(false, 7, 13, 666)));

        assertThat(sinSimulacion.emitir(4L, "Ana", "a@b.cl", 13.0).numero()).isEqualTo("BOL-00000004");
    }
}
