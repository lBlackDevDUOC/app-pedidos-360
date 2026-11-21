package cl.duoc.pedidos360.kafkaadmin.error;

import cl.duoc.pedidos360.kafkaadmin.service.AdminExceptions.ClusterNoDisponible;
import cl.duoc.pedidos360.kafkaadmin.service.AdminExceptions.ConfiguracionInvalida;
import cl.duoc.pedidos360.kafkaadmin.service.AdminExceptions.OperacionNoPermitida;
import cl.duoc.pedidos360.kafkaadmin.service.AdminExceptions.RecursoNoEncontrado;
import cl.duoc.pedidos360.kafkaadmin.service.AdminExceptions.RecursoYaExiste;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

import java.util.Arrays;
import java.util.Map;
import java.util.TreeMap;

/** Errores en formato RFC 7807 (application/problem+json). Validaciones con detalle por campo en "errores". */
@RestControllerAdvice
public class ErroresApiHandler {

    private static final Logger log = LoggerFactory.getLogger(ErroresApiHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail cuerpoInvalido(MethodArgumentNotValidException ex) {
        Map<String, String> errores = new TreeMap<>();
        ex.getBindingResult().getFieldErrors().forEach(e -> errores.merge(e.getField(), e.getDefaultMessage(),
                (a, b) -> a + "; " + b));
        ex.getBindingResult().getGlobalErrors().forEach(e -> errores.merge(e.getObjectName(), e.getDefaultMessage(),
                (a, b) -> a + "; " + b));
        return validacion(errores);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ProblemDetail parametrosInvalidos(HandlerMethodValidationException ex) {
        Map<String, String> errores = new TreeMap<>();
        ex.getParameterValidationResults().forEach(r -> r.getResolvableErrors().forEach(e ->
                errores.merge(r.getMethodParameter().getParameterName(), e.getDefaultMessage(), (a, b) -> a + "; " + b)));
        return validacion(errores);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ProblemDetail jsonIlegible(HttpMessageNotReadableException ex) {
        if (ex.getCause() instanceof InvalidFormatException formato && formato.getTargetType().isEnum()) {
            String campo = formato.getPath().isEmpty() ? "valor" : formato.getPath().get(0).getFieldName();
            return validacion(Map.of(campo, "valor '" + formato.getValue() + "' no permitido. Valores válidos: "
                    + Arrays.toString(formato.getTargetType().getEnumConstants())));
        }
        return problema(HttpStatus.BAD_REQUEST, "JSON inválido", "El cuerpo de la petición no es un JSON válido");
    }

    @ExceptionHandler(ConfiguracionInvalida.class)
    public ProblemDetail configuracionInvalida(ConfiguracionInvalida ex) {
        return problema(HttpStatus.BAD_REQUEST, "Configuración inválida", ex.getMessage());
    }

    @ExceptionHandler(RecursoNoEncontrado.class)
    public ProblemDetail noEncontrado(RecursoNoEncontrado ex) {
        return problema(HttpStatus.NOT_FOUND, "Recurso no encontrado", ex.getMessage());
    }

    @ExceptionHandler(RecursoYaExiste.class)
    public ProblemDetail yaExiste(RecursoYaExiste ex) {
        return problema(HttpStatus.CONFLICT, "El recurso ya existe", ex.getMessage());
    }

    @ExceptionHandler(OperacionNoPermitida.class)
    public ProblemDetail noPermitida(OperacionNoPermitida ex) {
        return problema(HttpStatus.CONFLICT, "Operación no permitida", ex.getMessage());
    }

    @ExceptionHandler(ClusterNoDisponible.class)
    public ProblemDetail clusterCaido(ClusterNoDisponible ex) {
        log.error("[KAFKA-ADMIN] {}", ex.getMessage(), ex.getCause());
        return problema(HttpStatus.SERVICE_UNAVAILABLE, "Clúster Kafka no disponible", ex.getMessage());
    }

    private static ProblemDetail validacion(Map<String, String> errores) {
        ProblemDetail p = problema(HttpStatus.BAD_REQUEST, "Datos inválidos", "La petición tiene campos inválidos");
        p.setProperty("errores", errores);
        return p;
    }

    private static ProblemDetail problema(HttpStatus estado, String titulo, String detalle) {
        ProblemDetail p = ProblemDetail.forStatusAndDetail(estado, detalle);
        p.setTitle(titulo);
        return p;
    }
}
