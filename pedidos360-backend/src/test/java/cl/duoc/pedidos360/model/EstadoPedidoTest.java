package cl.duoc.pedidos360.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EstadoPedidoTest {

    @ParameterizedTest
    @CsvSource({
            "CREADO, ACEPTADO", "CREADO, CANCELADO",
            "ACEPTADO, EN_PREPARACION", "ACEPTADO, CANCELADO",
            "EN_PREPARACION, DESPACHADO", "EN_PREPARACION, CANCELADO",
            "DESPACHADO, ENTREGADO"})
    void transicionesPermitidas(EstadoPedido desde, EstadoPedido hacia) {
        assertThat(desde.puedeCambiarA(hacia)).isTrue();
    }

    @ParameterizedTest
    @CsvSource({
            "CREADO, DESPACHADO",          // regla del caso: no se despacha sin aceptar
            "CREADO, ENTREGADO",
            "ACEPTADO, DESPACHADO",
            "DESPACHADO, CANCELADO",       // ya salió del local
            "ENTREGADO, CANCELADO",
            "CANCELADO, ACEPTADO",
            "ACEPTADO, ACEPTADO"})
    void transicionesProhibidas(EstadoPedido desde, EstadoPedido hacia) {
        assertThat(desde.puedeCambiarA(hacia)).isFalse();
    }

    @Test
    void entregadoYCanceladoSonFinales() {
        assertThat(EstadoPedido.ENTREGADO.esFinal()).isTrue();
        assertThat(EstadoPedido.CANCELADO.esFinal()).isTrue();
        assertThat(EstadoPedido.DESPACHADO.esFinal()).isFalse();
    }

    @Test
    void pedidoRecorreElFlujoCompletoYRegistraLaEntrega() {
        Pedido pedido = new Pedido(new Cliente(), 1000.0);
        assertThat(pedido.getEstado()).isEqualTo(EstadoPedido.CREADO);

        pedido.cambiarEstado(EstadoPedido.ACEPTADO);
        pedido.cambiarEstado(EstadoPedido.EN_PREPARACION);
        pedido.cambiarEstado(EstadoPedido.DESPACHADO);
        EstadoPedido anterior = pedido.cambiarEstado(EstadoPedido.ENTREGADO);

        assertThat(anterior).isEqualTo(EstadoPedido.DESPACHADO);
        assertThat(pedido.getFechaEntrega()).isNotNull();
    }

    @Test
    void pedidoRechazaDespacharSinAceptar() {
        Pedido pedido = new Pedido(new Cliente(), 1000.0);

        assertThatThrownBy(() -> pedido.cambiarEstado(EstadoPedido.DESPACHADO))
                .isInstanceOf(TransicionInvalidaException.class)
                .hasMessageContaining("CREADO")
                .hasMessageContaining("DESPACHADO");
        assertThat(pedido.getEstado()).isEqualTo(EstadoPedido.CREADO);
    }

    @Test
    void converterTraduceValoresHeredados() {
        EstadoPedidoConverter converter = new EstadoPedidoConverter();

        assertThat(converter.convertToEntityAttribute("PENDIENTE")).isEqualTo(EstadoPedido.CREADO);
        assertThat(converter.convertToEntityAttribute(null)).isEqualTo(EstadoPedido.CREADO);
        assertThat(converter.convertToEntityAttribute("aceptado")).isEqualTo(EstadoPedido.ACEPTADO);
        assertThat(converter.convertToDatabaseColumn(EstadoPedido.EN_PREPARACION)).isEqualTo("EN_PREPARACION");
    }
}
