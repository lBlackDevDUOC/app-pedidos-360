package cl.duoc.pedidos360.notificaciones.service;

import org.springframework.stereotype.Service;

import java.util.regex.Pattern;

/** Arma y envía las notificaciones al cliente. No conoce RabbitMQ: recibe datos simples. */
@Service
public class NotificacionService {

    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private final EmailSender emailSender;

    public NotificacionService(EmailSender emailSender) {
        this.emailSender = emailSender;
    }

    public void notificarPedidoRecibido(Long pedidoId, String clienteNombre, String clienteEmail, Double total) {
        emailSender.enviar(validarEmail(clienteEmail, pedidoId),
                "Recibimos tu pedido #" + pedidoId,
                "Hola %s, tu pedido #%d por $%,.0f fue recibido y estamos emitiendo tu comprobante."
                        .formatted(nombreOCliente(clienteNombre), pedidoId, total == null ? 0 : total));
    }

    public void notificarComprobanteEmitido(Long pedidoId, String numeroComprobante,
                                            String clienteNombre, String clienteEmail) {
        emailSender.enviar(validarEmail(clienteEmail, pedidoId),
                "Tu comprobante " + numeroComprobante + " está listo",
                "Hola %s, emitimos el comprobante %s de tu pedido #%d."
                        .formatted(nombreOCliente(clienteNombre), numeroComprobante, pedidoId));
    }

    private static String validarEmail(String email, Long pedidoId) {
        if (email == null || !EMAIL.matcher(email).matches()) {
            throw new DestinatarioInvalidoException("El pedido " + pedidoId + " no tiene un email válido: " + email);
        }
        return email;
    }

    private static String nombreOCliente(String nombre) {
        return nombre == null || nombre.isBlank() ? "cliente" : nombre;
    }
}
