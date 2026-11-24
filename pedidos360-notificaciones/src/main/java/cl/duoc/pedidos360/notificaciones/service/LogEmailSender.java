package cl.duoc.pedidos360.notificaciones.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Envío simulado: deja el email en el log. Suficiente para demostrar el flujo asíncrono sin un servidor SMTP. */
@Component
public class LogEmailSender implements EmailSender {

    private static final Logger log = LoggerFactory.getLogger(LogEmailSender.class);

    @Override
    public void enviar(String destinatario, String asunto, String cuerpo) {
        log.info("""
                        [EMAIL ENVIADO]
                          Para:   {}
                          Asunto: {}
                          {}""",
                destinatario, asunto, cuerpo);
    }
}
