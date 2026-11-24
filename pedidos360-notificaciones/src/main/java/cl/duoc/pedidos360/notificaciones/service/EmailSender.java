package cl.duoc.pedidos360.notificaciones.service;

/** Canal de envío de emails. Permite cambiar el proveedor (SMTP, AWS SES...) sin tocar la lógica de notificación. */
public interface EmailSender {

    void enviar(String destinatario, String asunto, String cuerpo);
}
