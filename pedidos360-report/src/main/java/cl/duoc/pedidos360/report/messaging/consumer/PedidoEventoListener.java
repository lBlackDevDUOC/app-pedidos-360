package cl.duoc.pedidos360.report.messaging.consumer;

import cl.duoc.pedidos360.report.messaging.event.EventoPedido;
import cl.duoc.pedidos360.report.service.ReporteService;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * Consumidor de {@code orders.events} (consumer group report-service). Es deliberadamente delgado: valida el
 * payload y delega en {@link ReporteService}, que no sabe nada de Kafka. Si algo falla, lanza la excepción y el
 * error handler (KafkaConsumerConfig) decide: reintentar o mandar al DLT.
 */
@Component
public class PedidoEventoListener {

    private static final Logger log = LoggerFactory.getLogger(PedidoEventoListener.class);

    private final ReporteService reporteService;
    private final Validator validator;

    public PedidoEventoListener(ReporteService reporteService, Validator validator) {
        this.reporteService = reporteService;
        this.validator = validator;
    }

    @KafkaListener(id = "report-pedidos", topics = "${app.kafka.topicos.pedidos}")
    public void onEvento(@Payload EventoPedido evento,
                         @Header(KafkaHeaders.RECEIVED_PARTITION) int particion,
                         @Header(KafkaHeaders.OFFSET) long offset) {
        validar(evento);
        log.info("[RECIBIDO] {} pedido={} estado={} particion={} offset={} eventoId={}",
                evento.tipo(), evento.pedidoId(), evento.estado(), particion, offset, evento.eventoId());
        reporteService.registrar(evento);
    }

    void validar(EventoPedido evento) {
        Set<ConstraintViolation<EventoPedido>> errores = validator.validate(evento);
        if (!errores.isEmpty()) {
            String detalle = errores.stream()
                    .map(e -> e.getPropertyPath() + " " + e.getMessage())
                    .sorted()
                    .collect(Collectors.joining(", "));
            throw new EventoInvalidoException("Evento incompleto: " + detalle);
        }
        if (evento.version() != EventoPedido.VERSION_SOPORTADA) {
            throw new EventoInvalidoException("Versión de evento no soportada: " + evento.version());
        }
    }
}
