package cl.duoc.pedidos360.service;

import cl.duoc.pedidos360.messaging.producer.EventoNegocioPublisher;
import cl.duoc.pedidos360.messaging.producer.PedidoEventPublisher;
import cl.duoc.pedidos360.model.Actor;
import cl.duoc.pedidos360.model.Cliente;
import cl.duoc.pedidos360.model.EstadoPedido;
import cl.duoc.pedidos360.model.ItemPedido;
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
    private final Actor operador = new Actor("oid-op", "Olga", List.of("Operador"), "10.0.0.2", "JUnit");
    private Catalogo catalogo;
    private PedidoService service;

    @BeforeEach
    void preparar() {
        repository = mock(PedidoRepository.class);
        publisher = mock(PedidoEventPublisher.class);
        eventosNegocio = mock(EventoNegocioPublisher.class);
        catalogo = mock(Catalogo.class);
        service = new PedidoService(repository, publisher, eventosNegocio, catalogo);
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

        Pedido resultado = service.cambiarEstado(7L, EstadoPedido.ACEPTADO, cliente, operador);

        assertThat(resultado.getEstado()).isEqualTo(EstadoPedido.ACEPTADO);
        verify(repository).save(pedido);
        verify(eventosNegocio).estadoCambiado(pedido, EstadoPedido.CREADO, operador);
    }

    @Test
    void cambiarEstado_transicionInvalida_noGuarda() {
        Cliente cliente = new Cliente("oid-1", "Ana", "ana@duoc.cl");
        cliente.setId(1L);
        when(repository.findById(7L)).thenReturn(Optional.of(new Pedido(cliente, 1000.0)));

        assertThatThrownBy(() -> service.cambiarEstado(7L, EstadoPedido.DESPACHADO, cliente, operador))
                .isInstanceOf(TransicionInvalidaException.class);
        verify(repository, never()).save(any());
        verify(eventosNegocio, never()).estadoCambiado(any(), any(), any());
    }

    @Test
    void cliente_puedeCancelarSuPedidoPeroNoAvanzarlo() {
        Cliente cliente = new Cliente("oid-1", "Ana", "ana@duoc.cl");
        cliente.setId(1L);
        when(repository.findById(7L)).thenReturn(Optional.of(new Pedido(cliente, 1000.0)));

        assertThatThrownBy(() -> service.cambiarEstado(7L, EstadoPedido.ACEPTADO, cliente, actor))
                .isInstanceOf(OperacionNoPermitidaException.class);
        assertThat(service.cambiarEstado(7L, EstadoPedido.CANCELADO, cliente, actor).getEstado())
                .isEqualTo(EstadoPedido.CANCELADO);
    }

    @Test
    void cliente_noVePedidosAjenos_operadorSi() {
        Cliente duena = new Cliente("oid-1", "Ana", "ana@duoc.cl");
        duena.setId(1L);
        Cliente otro = new Cliente("oid-2", "Beto", "b@duoc.cl");
        otro.setId(2L);
        when(repository.findById(7L)).thenReturn(Optional.of(new Pedido(duena, 1000.0)));

        assertThatThrownBy(() -> service.obtener(7L, otro, actor)).isInstanceOf(PedidoNoEncontradoException.class);
        assertThat(service.obtener(7L, otro, operador)).isNotNull();
    }

    @Test
    void crearConItems_usaPreciosDelCatalogoNoDelCliente() {
        when(catalogo.productos(List.of(1L, 2L))).thenReturn(List.of(
                new Catalogo.ProductoCatalogo(1L, "Pan", 1000.0, 10, true),
                new Catalogo.ProductoCatalogo(2L, "Café", 2500.0, 10, true)));

        Pedido p = service.crear(new Cliente("oid-1", "Ana", null),
                List.of(new PedidoService.LineaPedido(1L, 3), new PedidoService.LineaPedido(2L, 1)), actor);

        assertThat(p.getTotal()).isEqualTo(5500.0);
        assertThat(p.getItems()).extracting(ItemPedido::getNombre).containsExactly("Pan", "Café");
    }

    @Test
    void crearConProductoInactivo_falla() {
        when(catalogo.productos(List.of(9L))).thenReturn(List.of(new Catalogo.ProductoCatalogo(9L, "X", 1.0, 0, false)));

        assertThatThrownBy(() -> service.crear(new Cliente(), List.of(new PedidoService.LineaPedido(9L, 1)), actor))
                .isInstanceOf(Catalogo.ProductoNoDisponibleException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void aceptarDescuentaStock_yCancelarDespuesLoRepone() {
        Cliente cliente = new Cliente("oid-1", "Ana", null);
        cliente.setId(1L);
        Pedido pedido = new Pedido(cliente, List.of(new ItemPedido(1L, "Pan", 2, 1000.0)));
        pedido.setId(7L);
        when(repository.findById(7L)).thenReturn(Optional.of(pedido));

        service.cambiarEstado(7L, EstadoPedido.ACEPTADO, cliente, operador);
        verify(catalogo).descontarStock(7L, pedido.getItems());

        service.cambiarEstado(7L, EstadoPedido.CANCELADO, cliente, operador);
        verify(catalogo).reponerStock(7L);
    }

    @Test
    void sinStock_elPedidoNoSeAcepta() {
        Cliente cliente = new Cliente("oid-1", "Ana", null);
        cliente.setId(1L);
        Pedido pedido = new Pedido(cliente, List.of(new ItemPedido(1L, "Pan", 99, 1000.0)));
        pedido.setId(8L);
        when(repository.findById(8L)).thenReturn(Optional.of(pedido));
        org.mockito.Mockito.doThrow(new Catalogo.StockInsuficienteException("no hay"))
                .when(catalogo).descontarStock(any(), any());

        assertThatThrownBy(() -> service.cambiarEstado(8L, EstadoPedido.ACEPTADO, cliente, operador))
                .isInstanceOf(Catalogo.StockInsuficienteException.class);
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
