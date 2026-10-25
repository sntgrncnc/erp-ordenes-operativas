package com.sysman.ordenes.exception;

import com.sysman.ordenes.config.ContextoPeticion;
import com.sysman.ordenes.dto.RespuestaError;
import com.sysman.ordenes.dto.RespuestaError.DetalleError;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.data.mapping.PropertyReferenceException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static net.logstash.logback.argument.StructuredArguments.kv;

/**
 * Manejo global de excepciones: toda respuesta de error tiene el formato
 * {@link RespuestaError}, incluye el correlationId y nunca expone trazas
 * internas al cliente.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // ---------------------------------------------------------------- negocio
    @ExceptionHandler(NegocioException.class)
    public ResponseEntity<RespuestaError> manejarNegocio(NegocioException ex, HttpServletRequest req) {
        return responder(ex.getStatus(), ex.getCodigo(), ex.getMessage(), req, List.of(), ex);
    }

    // ------------------------------------------------------------ validaciones
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<RespuestaError> manejarValidacionCuerpo(MethodArgumentNotValidException ex,
                                                                  HttpServletRequest req) {
        List<DetalleError> detalles = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> new DetalleError(fe.getField(), fe.getDefaultMessage()))
                .toList();
        return responder(HttpStatus.BAD_REQUEST, "VALIDACION_FALLIDA",
                "La solicitud contiene datos inválidos", req, detalles, ex);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<RespuestaError> manejarValidacionParametros(HandlerMethodValidationException ex,
                                                                      HttpServletRequest req) {
        List<DetalleError> detalles = ex.getParameterValidationResults().stream()
                .flatMap(r -> r.getResolvableErrors().stream()
                        .map(e -> new DetalleError(r.getMethodParameter().getParameterName(), e.getDefaultMessage())))
                .toList();
        return responder(HttpStatus.BAD_REQUEST, "VALIDACION_FALLIDA",
                "La solicitud contiene parámetros inválidos", req, detalles, ex);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<RespuestaError> manejarConstraint(ConstraintViolationException ex, HttpServletRequest req) {
        List<DetalleError> detalles = ex.getConstraintViolations().stream()
                .map(v -> new DetalleError(v.getPropertyPath().toString(), v.getMessage()))
                .toList();
        return responder(HttpStatus.BAD_REQUEST, "VALIDACION_FALLIDA",
                "La solicitud contiene parámetros inválidos", req, detalles, ex);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<RespuestaError> manejarTipo(MethodArgumentTypeMismatchException ex, HttpServletRequest req) {
        String mensaje = "Valor inválido '%s' para el parámetro '%s'".formatted(ex.getValue(), ex.getName());
        return responder(HttpStatus.BAD_REQUEST, "PARAMETRO_INVALIDO", mensaje, req,
                List.of(new DetalleError(ex.getName(), mensaje)), ex);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<RespuestaError> manejarCuerpoIlegible(HttpMessageNotReadableException ex,
                                                                HttpServletRequest req) {
        return responder(HttpStatus.BAD_REQUEST, "CUERPO_INVALIDO",
                "El cuerpo de la solicitud es inválido o contiene valores no permitidos", req, List.of(), ex);
    }

    @ExceptionHandler(PropertyReferenceException.class)
    public ResponseEntity<RespuestaError> manejarOrdenamiento(PropertyReferenceException ex, HttpServletRequest req) {
        return responder(HttpStatus.BAD_REQUEST, "ORDENAMIENTO_INVALIDO",
                "No es posible ordenar por '" + ex.getPropertyName() + "'", req, List.of(), ex);
    }

    // ------------------------------------------------------------ concurrencia
    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<RespuestaError> manejarOptimista(ObjectOptimisticLockingFailureException ex,
                                                           HttpServletRequest req) {
        return responder(HttpStatus.CONFLICT, "CONFLICTO_CONCURRENCIA",
                "El recurso fue modificado por otro usuario. Consulte nuevamente e intente otra vez", req, List.of(), ex);
    }

    @ExceptionHandler(PessimisticLockingFailureException.class)
    public ResponseEntity<RespuestaError> manejarPesimista(PessimisticLockingFailureException ex,
                                                           HttpServletRequest req) {
        return responder(HttpStatus.CONFLICT, "RECURSO_BLOQUEADO",
                "El recurso está siendo modificado por otra transacción. Intente nuevamente", req, List.of(), ex);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<RespuestaError> manejarIntegridad(DataIntegrityViolationException ex, HttpServletRequest req) {
        return responder(HttpStatus.CONFLICT, "INTEGRIDAD_DATOS",
                "La operación viola una restricción de integridad de datos", req, List.of(), ex);
    }

    // ---------------------------------------------------------------- genérico
    @ExceptionHandler(Exception.class)
    public ResponseEntity<RespuestaError> manejarGenerico(Exception ex, HttpServletRequest req) {
        // Excepciones estándar de Spring MVC (405, 404 de recurso, 415, etc.)
        if (ex instanceof ErrorResponse er) {
            HttpStatusCode status = er.getStatusCode();
            String detalle = er.getBody().getDetail() != null ? er.getBody().getDetail() : ex.getMessage();
            return responder(status, "ERROR_SOLICITUD", detalle, req, List.of(), ex);
        }
        return responder(HttpStatus.INTERNAL_SERVER_ERROR, "ERROR_INTERNO",
                "Ocurrió un error inesperado. Reporte el correlationId al soporte", req, List.of(), ex);
    }

    // ----------------------------------------------------------------------------
    private ResponseEntity<RespuestaError> responder(HttpStatusCode status, String codigo, String mensaje,
                                                     HttpServletRequest req, List<DetalleError> detalles,
                                                     Exception ex) {
        if (status.is5xxServerError()) {
            log.error("Error no controlado", kv("evento", "ERROR_API"), kv("codigo", codigo),
                    kv("status", status.value()), kv("ruta", req.getRequestURI()), ex);
        } else {
            log.warn("Solicitud rechazada: {}", mensaje, kv("evento", "ERROR_API"), kv("codigo", codigo),
                    kv("status", status.value()), kv("ruta", req.getRequestURI()));
        }
        RespuestaError cuerpo = new RespuestaError(
                OffsetDateTime.now(ZoneOffset.UTC),
                status.value(),
                codigo,
                mensaje,
                req.getRequestURI(),
                ContextoPeticion.correlationId(),
                detalles);
        return ResponseEntity.status(status).body(cuerpo);
    }
}
