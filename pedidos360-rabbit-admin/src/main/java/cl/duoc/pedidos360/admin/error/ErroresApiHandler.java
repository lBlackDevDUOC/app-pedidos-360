package cl.duoc.pedidos360.admin.error;

import cl.duoc.pedidos360.admin.service.ConfiguracionInvalidaException;
import cl.duoc.pedidos360.admin.service.RecursoNoEncontradoException;
import cl.duoc.pedidos360.admin.service.RecursoProtegidoException;
import cl.duoc.pedidos360.admin.service.RecursoYaExisteException;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.validation.method.ParameterErrors;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.RestClientException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

import java.util.Arrays;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.BinaryOperator;

/**
 * Traduce los errores a respuestas RFC 7807 (application/problem+json) con mensajes claros.
 * Los errores de validación incluyen el detalle por campo en la propiedad "errores".
 */
@RestControllerAdvice
public class ErroresApiHandler {

    private static final Logger log = LoggerFactory.getLogger(ErroresApiHandler.class);

    /** Cuerpo JSON con campos inválidos (@Valid). */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail cuerpoInvalido(MethodArgumentNotValidException ex) {
        Map<String, String> errores = new TreeMap<>();
        ex.getBindingResult().getFieldErrors().forEach(e -> errores.merge(e.getField(), e.getDefaultMessage(), unir()));
        ex.getBindingResult().getGlobalErrors().forEach(e -> errores.merge(e.getObjectName(), e.getDefaultMessage(), unir()));
        return validacion(errores);
    }

    /** Parámetros de ruta o de consulta inválidos (@Pattern, @NotBlank sobre @PathVariable/@RequestParam). */
    @ExceptionHandler(HandlerMethodValidationException.class)
    public ProblemDetail parametrosInvalidos(HandlerMethodValidationException ex) {
        Map<String, String> errores = new TreeMap<>();
        ex.getParameterValidationResults().forEach(resultado -> {
            if (resultado instanceof ParameterErrors errs) {
                for (FieldError e : errs.getFieldErrors()) {
                    errores.merge(e.getField(), e.getDefaultMessage(), unir());
                }
            } else {
                String parametro = resultado.getMethodParameter().getParameterName();
                resultado.getResolvableErrors().forEach(e -> errores.merge(parametro, e.getDefaultMessage(), unir()));
            }
        });
        return validacion(errores);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ProblemDetail parametroFaltante(MissingServletRequestParameterException ex) {
        return validacion(Map.of(ex.getParameterName(), "es obligatorio"));
    }

    /** JSON mal formado o un valor que no calza con el tipo (ej. tipo de exchange inexistente). */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ProblemDetail jsonIlegible(HttpMessageNotReadableException ex) {
        if (ex.getCause() instanceof InvalidFormatException formato && formato.getTargetType().isEnum()) {
            String campo = formato.getPath().isEmpty() ? "valor" : formato.getPath().get(0).getFieldName();
            return validacion(Map.of(campo, "valor '" + formato.getValue() + "' no permitido. Valores válidos: "
                    + Arrays.toString(formato.getTargetType().getEnumConstants())));
        }
        return problema(HttpStatus.BAD_REQUEST, "JSON inválido", "El cuerpo de la petición no es un JSON válido");
    }

    @ExceptionHandler(RecursoNoEncontradoException.class)
    public ProblemDetail noEncontrado(RecursoNoEncontradoException ex) {
        return problema(HttpStatus.NOT_FOUND, "Recurso no encontrado", ex.getMessage());
    }

    @ExceptionHandler(RecursoYaExisteException.class)
    public ProblemDetail yaExiste(RecursoYaExisteException ex) {
        return problema(HttpStatus.CONFLICT, "El recurso ya existe", ex.getMessage());
    }

    @ExceptionHandler(RecursoProtegidoException.class)
    public ProblemDetail protegido(RecursoProtegidoException ex) {
        return problema(HttpStatus.CONFLICT, "Recurso protegido", ex.getMessage());
    }

    @ExceptionHandler(ConfiguracionInvalidaException.class)
    public ProblemDetail configuracionInvalida(ConfiguracionInvalidaException ex) {
        return problema(HttpStatus.UNPROCESSABLE_ENTITY, "Configuración inválida", ex.getMessage());
    }

    /** RabbitMQ caído o sin acceso (AMQP o API de Management). */
    @ExceptionHandler({AmqpException.class, RestClientException.class})
    public ProblemDetail brokerNoDisponible(RuntimeException ex) {
        log.error("[ADMIN] Error comunicándose con RabbitMQ", ex);
        return problema(HttpStatus.SERVICE_UNAVAILABLE, "RabbitMQ no disponible",
                "No se pudo completar la operación en RabbitMQ: " + ex.getMessage());
    }

    private static ProblemDetail validacion(Map<String, String> errores) {
        ProblemDetail problema = problema(HttpStatus.BAD_REQUEST, "Datos inválidos",
                "La petición tiene " + errores.size() + " campo(s) inválido(s)");
        problema.setProperty("errores", errores);
        return problema;
    }

    private static ProblemDetail problema(HttpStatus estado, String titulo, String detalle) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(estado, detalle);
        problema.setTitle(titulo);
        return problema;
    }

    private static BinaryOperator<String> unir() {
        return (a, b) -> a + "; " + b;
    }
}
