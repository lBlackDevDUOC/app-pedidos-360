package cl.duoc.pedidos360.notificaciones.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class NotificacionServiceTest {

    private final EmailSender emailSender = mock(EmailSender.class);
    private final NotificacionService service = new NotificacionService(emailSender);

    @Test
    void pedidoRecibido_enviaEmailAlCliente() {
        service.notificarPedidoRecibido(12L, "Ana", "ana@duoc.cl", 11900.0);

        verify(emailSender).enviar(eq("ana@duoc.cl"), contains("#12"), contains("Ana"));
    }

    @Test
    void comprobanteEmitido_incluyeElNumero() {
        service.notificarComprobanteEmitido(12L, "BOL-00000012", "Ana", "ana@duoc.cl");

        verify(emailSender).enviar(eq("ana@duoc.cl"), contains("BOL-00000012"), contains("BOL-00000012"));
    }

    @Test
    void emailInvalido_lanzaErrorNoRecuperable() {
        assertThatThrownBy(() -> service.notificarPedidoRecibido(12L, "Ana", "no-es-email", 1000.0))
                .isInstanceOf(DestinatarioInvalidoException.class);
        assertThatThrownBy(() -> service.notificarPedidoRecibido(12L, "Ana", null, 1000.0))
                .isInstanceOf(DestinatarioInvalidoException.class);
        verifyNoInteractions(emailSender);
    }
}
