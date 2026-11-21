package cl.duoc.pedidos360.controller;

import cl.duoc.pedidos360.model.TransicionInvalidaException;
import cl.duoc.pedidos360.service.OperacionNoPermitidaException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.stream.Collectors;

/** Traduce los errores de negocio y de validación a respuestas HTTP claras (formato RFC 7807 Problem Details). */
@RestControllerAdvice
public class ErroresApiHandler {

    @ExceptionHandler(TransicionInvalidaException.class)
    public ProblemDetail transicionInvalida(TransicionInvalidaException e) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
        problema.setTitle("Transición de estado no permitida");
        problema.setProperty("estadoActual", e.getActual());
        problema.setProperty("estadoSolicitado", e.getDestino());
        problema.setProperty("permitidos", e.getActual().siguientes());
        return problema;
    }

    @ExceptionHandler(OperacionNoPermitidaException.class)
    public ProblemDetail operacionNoPermitida(OperacionNoPermitidaException e) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, e.getMessage());
        problema.setTitle("Operación no permitida para tu rol");
        return problema;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail datosInvalidos(MethodArgumentNotValidException e) {
        String detalle = e.getBindingResult().getFieldErrors().stream()
                .map(err -> err.getDefaultMessage())
                .collect(Collectors.joining(", "));
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detalle);
        problema.setTitle("Datos inválidos");
        return problema;
    }

    /** JSON mal formado o un estado que no existe en el enum (ej. "ENVIADO"). */
    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    public ProblemDetail cuerpoIlegible(Exception e) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
                "Petición inválida. Estados válidos: CREADO, ACEPTADO, EN_PREPARACION, DESPACHADO, ENTREGADO, CANCELADO");
        problema.setTitle("Petición inválida");
        return problema;
    }
}
