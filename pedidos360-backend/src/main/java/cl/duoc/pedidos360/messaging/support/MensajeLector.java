package cl.duoc.pedidos360.messaging.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.springframework.amqp.core.Message;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Convierte el cuerpo JSON de un mensaje al evento esperado y valida sus campos obligatorios.
 * Se hace dentro del consumidor (y no automáticamente en Spring) para que un mensaje corrupto
 * también pase por el manejo explícito de ACK/NACK y termine en la DLQ.
 */
@Component
public class MensajeLector {

    private final ObjectMapper objectMapper;
    private final Validator validator;

    public MensajeLector(ObjectMapper objectMapper, Validator validator) {
        this.objectMapper = objectMapper;
        this.validator = validator;
    }

    public <T> T leer(Message mensaje, Class<T> tipo) {
        T evento;
        try {
            evento = objectMapper.readValue(mensaje.getBody(), tipo);
        } catch (IOException e) {
            throw new MensajeInvalidoException("El cuerpo no es un JSON válido de " + tipo.getSimpleName(), e);
        }
        if (evento == null) {
            throw new MensajeInvalidoException("Mensaje vacío, se esperaba " + tipo.getSimpleName());
        }

        Set<ConstraintViolation<T>> violaciones = validator.validate(evento);
        if (!violaciones.isEmpty()) {
            String detalle = violaciones.stream()
                    .map(v -> v.getPropertyPath() + " " + v.getMessage())
                    .sorted()
                    .collect(Collectors.joining(", "));
            throw new MensajeInvalidoException("Mensaje incompleto (" + tipo.getSimpleName() + "): " + detalle);
        }
        return evento;
    }

    /** Texto corto del mensaje para dejarlo en los logs. */
    public static String resumen(Message mensaje) {
        return "messageId=" + mensaje.getMessageProperties().getMessageId()
                + " cola=" + mensaje.getMessageProperties().getConsumerQueue();
    }
}
