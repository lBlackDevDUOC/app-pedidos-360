package cl.duoc.pedidos360.messaging.producer;

import cl.duoc.pedidos360.messaging.config.KafkaTopicosProperties;
import cl.duoc.pedidos360.messaging.event.EventoAuditoria;
import cl.duoc.pedidos360.messaging.event.EventoPedido;
import cl.duoc.pedidos360.model.Actor;
import cl.duoc.pedidos360.model.Cliente;
import cl.duoc.pedidos360.model.EstadoPedido;
import cl.duoc.pedidos360.model.Pedido;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class KafkaEventoNegocioPublisherTest {

    private KafkaTemplate<String, Object> kafkaTemplate;
    private KafkaEventoNegocioPublisher publisher;
    private final Actor actor = new Actor("oid-ana", "Ana", List.of("Operador"), "190.1.2.3", "Firefox");

    @BeforeEach
    @SuppressWarnings("unchecked")
    void preparar() {
        kafkaTemplate = mock(KafkaTemplate.class);
        when(kafkaTemplate.send(anyString(), anyString(), any()))
                .thenReturn(new CompletableFuture<SendResult<String, Object>>());
        var props = new KafkaTopicosProperties(new KafkaTopicosProperties.Topicos("orders.events", "audit.timeline"));
        publisher = new KafkaEventoNegocioPublisher(kafkaTemplate, props);
    }

    private Pedido pedido() {
        Cliente cliente = new Cliente("oid-cli", "Cliente Uno", "c@duoc.cl");
        Pedido pedido = new Pedido(cliente, 12000.0);
        pedido.setId(15L);
        return pedido;
    }

    @Test
    void cambioDeEstado_publicaEnOrdersEventsConKeyDelPedido() {
        Pedido pedido = pedido();
        pedido.cambiarEstado(EstadoPedido.ACEPTADO);

        publisher.estadoCambiado(pedido, EstadoPedido.CREADO, actor);

        ArgumentCaptor<Object> valor = ArgumentCaptor.forClass(Object.class);
        verify(kafkaTemplate).send(eq("orders.events"), eq("15"), valor.capture());
        EventoPedido evento = (EventoPedido) valor.getValue();
        assertThat(evento.tipo()).isEqualTo("OrderAccepted");
        assertThat(evento.estadoAnterior()).isEqualTo("CREADO");
        assertThat(evento.estado()).isEqualTo("ACEPTADO");
        assertThat(evento.correlationId()).isEqualTo("pedido-15");
        assertThat(evento.eventoId()).isNotBlank();
        assertThat(evento.actorId()).isEqualTo("oid-ana");
    }

    @Test
    void cadaHecho_publicaTambienUnRegistroDeAuditoriaConQuienYDesdeDonde() {
        publisher.pedidoCreado(pedido(), actor);

        ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object> valor = ArgumentCaptor.forClass(Object.class);
        verify(kafkaTemplate).send(eq("audit.timeline"), key.capture(), valor.capture());
        EventoAuditoria auditoria = (EventoAuditoria) valor.getValue();
        assertThat(key.getValue()).isEqualTo(auditoria.eventoId());   // key = id del evento (compactación segura)
        assertThat(auditoria.accion()).isEqualTo("PEDIDO_CREADO");
        assertThat(auditoria.actorNombre()).isEqualTo("Ana");
        assertThat(auditoria.actorRoles()).containsExactly("Operador");
        assertThat(auditoria.ip()).isEqualTo("190.1.2.3");
    }

    @Test
    void siKafkaNoResponde_elNegocioNoSeInterrumpe() {
        when(kafkaTemplate.send(anyString(), anyString(), any())).thenThrow(new RuntimeException("timeout"));

        assertThatCode(() -> publisher.pedidoCreado(pedido(), actor)).doesNotThrowAnyException();
    }

    @Test
    void tipoDeEventoPorEstado() {
        assertThat(KafkaEventoNegocioPublisher.tipoDeEvento(EstadoPedido.CREADO)).isEqualTo("OrderCreated");
        assertThat(KafkaEventoNegocioPublisher.tipoDeEvento(EstadoPedido.ENTREGADO)).isEqualTo("OrderDelivered");
        assertThat(KafkaEventoNegocioPublisher.tipoDeEvento(EstadoPedido.CANCELADO)).isEqualTo("OrderCancelled");
    }
}
