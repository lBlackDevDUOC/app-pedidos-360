package cl.duoc.pedidos360.admin.service;

import cl.duoc.pedidos360.admin.config.AdminProperties;
import cl.duoc.pedidos360.admin.dto.CrearBindingRequest;
import cl.duoc.pedidos360.admin.dto.CrearColaRequest;
import cl.duoc.pedidos360.admin.dto.CrearExchangeRequest;
import cl.duoc.pedidos360.admin.dto.TipoExchange;
import cl.duoc.pedidos360.admin.rabbit.RabbitManagementClient;
import cl.duoc.pedidos360.admin.rabbit.RabbitManagementClient.BindingInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Exchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueInformation;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** El servicio valida antes de tocar RabbitMQ y traduce cada operación a la llamada correcta de AmqpAdmin. */
class RabbitAdminServiceTest {

    private AmqpAdmin amqpAdmin;
    private RabbitManagementClient management;
    private RabbitAdminService service;

    @BeforeEach
    void preparar() {
        amqpAdmin = mock(AmqpAdmin.class);
        management = mock(RabbitManagementClient.class);
        AdminProperties props = new AdminProperties(
                new AdminProperties.Management("http://x", "/", "u", "p"),
                List.of("pedidos.", "pedidos360."),
                new AdminProperties.Alertas(30000, ".dlq", 1));
        service = new RabbitAdminService(amqpAdmin, management, props);
    }

    @Test
    void crearCola_declaraConTtlLargoYDlx() {
        when(management.existeExchange("demo.dlx")).thenReturn(true);

        service.crearCola(new CrearColaRequest("demo.q", null, 60000, 100, "demo.dlx", "demo.q"));

        ArgumentCaptor<Queue> cola = ArgumentCaptor.forClass(Queue.class);
        verify(amqpAdmin).declareQueue(cola.capture());
        assertThat(cola.getValue().getName()).isEqualTo("demo.q");
        assertThat(cola.getValue().isDurable()).isTrue();
        assertThat(cola.getValue().getArguments())
                .containsEntry("x-message-ttl", 60000)
                .containsEntry("x-max-length", 100L)
                .containsEntry("x-dead-letter-exchange", "demo.dlx")
                .containsEntry("x-dead-letter-routing-key", "demo.q");
    }

    @Test
    void crearCola_existente_409SinDeclarar() {
        when(amqpAdmin.getQueueInfo("demo.q")).thenReturn(new QueueInformation("demo.q", 0, 0));

        assertThatThrownBy(() -> service.crearCola(new CrearColaRequest("demo.q", null, null, null, null, null)))
                .isInstanceOf(RecursoYaExisteException.class);
        verify(amqpAdmin, never()).declareQueue(any());
    }

    @Test
    void crearCola_conDlxInexistente_esConfiguracionInvalida() {
        when(management.existeExchange("no.existe")).thenReturn(false);

        assertThatThrownBy(() -> service.crearCola(new CrearColaRequest("demo.q", null, null, null, "no.existe", null)))
                .isInstanceOf(ConfiguracionInvalidaException.class);
        verify(amqpAdmin, never()).declareQueue(any());
    }

    @Test
    void crearCola_conPrefijoDelSistema_protegida() {
        assertThatThrownBy(() -> service.crearCola(new CrearColaRequest("pedidos.nueva", null, null, null, null, null)))
                .isInstanceOf(RecursoProtegidoException.class);
    }

    @Test
    void eliminarCola_inexistente_404() {
        assertThatThrownBy(() -> service.eliminarCola("nada.q")).isInstanceOf(RecursoNoEncontradoException.class);
        verify(amqpAdmin, never()).deleteQueue(anyString());
    }

    @Test
    void eliminarCola_delSistema_noSePermite() {
        assertThatThrownBy(() -> service.eliminarCola("pedidos.documento-generado.q"))
                .isInstanceOf(RecursoProtegidoException.class);
        verify(amqpAdmin, never()).deleteQueue(anyString());
    }

    @Test
    void purgarCola_delSistema_siSePermite() {
        when(amqpAdmin.getQueueInfo("pedidos.documento-generado.dlq"))
                .thenReturn(new QueueInformation("pedidos.documento-generado.dlq", 3, 0));
        when(amqpAdmin.purgeQueue("pedidos.documento-generado.dlq")).thenReturn(3);

        assertThat(service.purgarCola("pedidos.documento-generado.dlq").mensajesEliminados()).isEqualTo(3);
    }

    @Test
    void crearExchange_usaElTipoIndicado() {
        service.crearExchange(new CrearExchangeRequest("demo.ex", TipoExchange.FANOUT, false));

        ArgumentCaptor<Exchange> exchange = ArgumentCaptor.forClass(Exchange.class);
        verify(amqpAdmin).declareExchange(exchange.capture());
        assertThat(exchange.getValue().getType()).isEqualTo("fanout");
        assertThat(exchange.getValue().isDurable()).isFalse();
    }

    @Test
    void crearBinding_colaInexistente_404() {
        assertThatThrownBy(() -> service.crearBinding(new CrearBindingRequest("demo.ex", "nada.q", "x")))
                .isInstanceOf(RecursoNoEncontradoException.class);
        verify(amqpAdmin, never()).declareBinding(any());
    }

    @Test
    void crearBinding_declaraColaExchangeYRoutingKey() {
        when(amqpAdmin.getQueueInfo("demo.q")).thenReturn(new QueueInformation("demo.q", 0, 0));
        when(management.existeExchange("demo.ex")).thenReturn(true);
        when(management.listarBindings()).thenReturn(List.of());

        service.crearBinding(new CrearBindingRequest("demo.ex", "demo.q", "reporte.*"));

        ArgumentCaptor<Binding> binding = ArgumentCaptor.forClass(Binding.class);
        verify(amqpAdmin).declareBinding(binding.capture());
        assertThat(binding.getValue().getExchange()).isEqualTo("demo.ex");
        assertThat(binding.getValue().getDestination()).isEqualTo("demo.q");
        assertThat(binding.getValue().getRoutingKey()).isEqualTo("reporte.*");
    }

    @Test
    void eliminarBinding_inexistente_404() {
        when(management.listarBindings()).thenReturn(List.of(new BindingInfo("demo.ex", "demo.q", "queue", "otra")));

        assertThatThrownBy(() -> service.eliminarBinding("demo.ex", "demo.q", "reporte.*"))
                .isInstanceOf(RecursoNoEncontradoException.class);
        verify(amqpAdmin, never()).removeBinding(any());
    }
}
