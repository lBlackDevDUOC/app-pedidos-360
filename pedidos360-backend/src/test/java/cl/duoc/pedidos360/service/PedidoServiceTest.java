package cl.duoc.pedidos360.service;

import cl.duoc.pedidos360.messaging.producer.EventoNegocioPublisher;
import cl.duoc.pedidos360.messaging.producer.PedidoEventPublisher;
import cl.duoc.pedidos360.model.Actor;
import cl.duoc.pedidos360.model.Cliente;
import cl.duoc.pedidos360.model.EstadoPedido;
import cl.duoc.pedidos360.model.Pedido;
import cl.duoc.pedidos360.model.TransicionInvalidaException;
import cl.duoc.pedidos360.repository.PedidoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PedidoServiceTest {

    private PedidoRepository repository;
    private PedidoEventPublisher publisher;
    private EventoNegocioPublisher eventosNegocio;
    private final Actor actor = new Actor("oid-1", "Ana", List.of("Cliente"), "10.0.0.1", "JUnit");
    private PedidoService service;

    @BeforeEach
    void preparar() {
        repository = mock(PedidoRepository.class);
        publisher = mock(PedidoEventPublisher.class);
        eventosNegocio = mock(EventoNegocioPublisher.class);
        service = new PedidoService(repository, publisher, eventosNegocio);
        when(repository.save(any(Pedido.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void crear_guardaYPublicaElEvento() {
        Pedido pedido = service.crear(new Cliente("oid-1", "Ana", "ana@duoc.cl"), 15000.0, actor);

        verify(repository).save(pedido);
        verify(publisher).publicarPedidoCreado(pedido);
        verify(eventosNegocio).pedidoCreado(pedido, actor);
    }

    @Test
    void registrarComprobante_guardaNumeroYFecha() {
        Pedido pedido = new Pedido(new Cliente(), 1000.0);
        when(repository.findById(7L)).thenReturn(Optional.of(pedido));
        LocalDateTime fecha = LocalDateTime.of(2026, 10, 18, 10, 0);

        service.registrarComprobante(7L, "BOL-00000007", fecha);

        assertThat(pedido.getNumeroComprobante()).isEqualTo("BOL-00000007");
        assertThat(pedido.getFechaComprobante()).isEqualTo(fecha);
        verify(repository).save(pedido);
    }

    @Test
    void registrarComprobante_esIdempotenteAnteMensajesDuplicados() {
        Pedido pedido = new Pedido(new Cliente(), 1000.0);
        pedido.setNumeroComprobante("BOL-00000007");
        when(repository.findById(7L)).thenReturn(Optional.of(pedido));

        service.registrarComprobante(7L, "BOL-00000007", LocalDateTime.now());

        verify(repository, never()).save(any());
    }

    @Test
    void cambiarEstado_avanzaYGuarda() {
        Cliente cliente = new Cliente("oid-1", "Ana", "ana@duoc.cl");
        cliente.setId(1L);
        Pedido pedido = new Pedido(cliente, 1000.0);
        when(repository.findById(7L)).thenReturn(Optional.of(pedido));

        Pedido resultado = service.cambiarEstado(7L, EstadoPedido.ACEPTADO, cliente, actor);

        assertThat(resultado.getEstado()).isEqualTo(EstadoPedido.ACEPTADO);
        verify(repository).save(pedido);
        verify(eventosNegocio).estadoCambiado(pedido, EstadoPedido.CREADO, actor);
    }

    @Test
    void cambiarEstado_transicionInvalida_noGuarda() {
        Cliente cliente = new Cliente("oid-1", "Ana", "ana@duoc.cl");
        cliente.setId(1L);
        when(repository.findById(7L)).thenReturn(Optional.of(new Pedido(cliente, 1000.0)));

        assertThatThrownBy(() -> service.cambiarEstado(7L, EstadoPedido.DESPACHADO, cliente, actor))
                .isInstanceOf(TransicionInvalidaException.class);
        verify(repository, never()).save(any());
        verify(eventosNegocio, never()).estadoCambiado(any(), any(), any());
    }

    @Test
    void registrarComprobante_pedidoInexistente_lanzaExcepcion() {
        when(repository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.registrarComprobante(99L, "BOL-1", LocalDateTime.now()))
                .isInstanceOf(PedidoNoEncontradoException.class);
    }
}
