package com.proyecto.servicios.controller.advice;

import com.proyecto.servicios.client.GestoPagoAuthException;
import com.proyecto.servicios.client.GestoPagoCatalogException;
import com.proyecto.servicios.models.response.GenericResponse;
import com.proyecto.servicios.service.exception.AutenticacionException;
import com.proyecto.servicios.service.exception.NegocioException;
import jakarta.validation.ConstraintViolationException;
import java.sql.SQLException;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {
    private static final String DATOS_INVALIDOS = "Verifique la información capturada e intente nuevamente.";
    private static final String ERROR_INTERNO = "Ocurrió un error interno en la API";
    private static final String SOLICITUD_INVALIDA = "Solicitud inválida: revisa los campos, tipos y valores permitidos";

    @ExceptionHandler({MethodArgumentNotValidException.class, BindException.class})
    ResponseEntity<GenericResponse<Void>> validacion(BindException exception) {
        Map<String, String> errores = new TreeMap<>();
        exception.getAllErrors().forEach(error -> agregarError(errores,
                error instanceof FieldError campo ? campo.getField() : "solicitud", error));
        return datosInvalidos(errores);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<GenericResponse<Void>> restricciones(ConstraintViolationException exception) {
        Map<String, String> errores = new TreeMap<>();
        exception.getConstraintViolations().forEach(error ->
                errores.merge(error.getPropertyPath().toString(), error.getMessage(), this::combinarMensajes));
        return datosInvalidos(errores);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    ResponseEntity<GenericResponse<Void>> argumentos(HandlerMethodValidationException exception) {
        if (exception.isForReturnValue()) return interno(exception);
        Map<String, String> errores = new TreeMap<>();
        exception.getAllValidationResults().forEach(resultado -> {
            String parametro = resultado.getMethodParameter().getParameterName();
            resultado.getResolvableErrors().forEach(error -> agregarError(errores,
                    error instanceof FieldError campo ? campo.getField()
                            : parametro == null ? "solicitud" : parametro, error));
        });
        return datosInvalidos(errores);
    }

    @ExceptionHandler(NegocioException.class)
    ResponseEntity<GenericResponse<Void>> negocio(NegocioException exception) {
        return respuesta(exception.getStatus(), exception.getMessage());
    }

    @ExceptionHandler(AutenticacionException.class)
    ResponseEntity<GenericResponse<Void>> autenticacion(AutenticacionException exception) {
        return switch (exception.getTipo()) {
            case USUARIO_DUPLICADO -> respuesta(HttpStatus.CONFLICT, "El usuario ya está registrado");
            case CREDENCIALES_INVALIDAS -> respuesta(HttpStatus.UNAUTHORIZED, "Usuario o contraseña incorrectos");
            case CUENTA_INACTIVA -> respuesta(HttpStatus.FORBIDDEN, "El usuario o cliente está inactivo");
        };
    }

    @ExceptionHandler({AuthenticationException.class, AccessDeniedException.class})
    ResponseEntity<GenericResponse<Void>> seguridad(RuntimeException exception) {
        return exception instanceof AccessDeniedException
                ? respuesta(HttpStatus.FORBIDDEN, "No tienes permiso para realizar esta operación")
                : respuesta(HttpStatus.UNAUTHORIZED, "Se requiere iniciar sesión");
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<GenericResponse<Void>> integridad(DataIntegrityViolationException exception) {
        for (Throwable causa = exception; causa != null; causa = causa.getCause()) {
            if (causa instanceof SQLException sql
                    && ("23514".equals(sql.getSQLState()) || "23502".equals(sql.getSQLState()))) {
                return respuesta(HttpStatus.BAD_REQUEST, "Los datos incumplen una validación del registro");
            }
        }
        return respuesta(HttpStatus.CONFLICT, "La operación incumple una restricción del registro");
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    ResponseEntity<GenericResponse<Void>> solicitudInvalida() {
        return respuesta(HttpStatus.BAD_REQUEST, SOLICITUD_INVALIDA);
    }

    @ExceptionHandler({GestoPagoCatalogException.class, GestoPagoAuthException.class})
    ResponseEntity<GenericResponse<Void>> proveedorExterno(RuntimeException exception) {
        log.error("Error del proveedor externo ({})", exception.getClass().getSimpleName());
        return respuesta(HttpStatus.BAD_GATEWAY, "No fue posible comunicarse con el proveedor de servicios");
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<GenericResponse<Void>> interno(Exception exception) {
        if (!(exception instanceof ErrorResponse error)) {
            log.error("Error no controlado en la API", exception);
            return respuesta(HttpStatus.INTERNAL_SERVER_ERROR, ERROR_INTERNO);
        }
        // Spring ya conoce el estado y encabezados como Allow; solo sustituimos el mensaje.
        int estado = error.getStatusCode().value();
        if (estado >= 500) log.error("Error al procesar la solicitud", exception);
        String mensaje = switch (estado) {
            case 400 -> SOLICITUD_INVALIDA;
            case 404 -> "Ruta no encontrada";
            case 405 -> "Método HTTP no permitido para esta ruta";
            case 406 -> "La API devuelve sus respuestas en formato JSON";
            case 415 -> "Tipo de contenido no permitido";
            default -> estado >= 500 ? ERROR_INTERNO : "No fue posible procesar la solicitud";
        };
        HttpHeaders encabezados = new HttpHeaders();
        encabezados.putAll(error.getHeaders());
        encabezados.setContentType(MediaType.APPLICATION_JSON);
        return new ResponseEntity<>(GenericResponse.error(mensaje), encabezados, error.getStatusCode());
    }

    private void agregarError(Map<String, String> errores, String campo, MessageSourceResolvable error) {
        String mensaje = error instanceof FieldError field && field.isBindingFailure()
                ? "tipo o formato inválido"
                : error.getDefaultMessage() == null ? "valor inválido" : error.getDefaultMessage();
        errores.merge(campo, mensaje, this::combinarMensajes);
    }

    private String combinarMensajes(String primero, String segundo) {
        return primero.equals(segundo) ? primero : primero + "; " + segundo;
    }

    private ResponseEntity<GenericResponse<Void>> datosInvalidos(Map<String, String> errores) {
        String detalle = errores.entrySet().stream()
                .map(error -> error.getKey() + ": " + error.getValue())
                .collect(Collectors.joining("; "));
        return respuesta(HttpStatus.BAD_REQUEST,
                detalle.isEmpty() ? DATOS_INVALIDOS : DATOS_INVALIDOS + " " + detalle);
    }

    private ResponseEntity<GenericResponse<Void>> respuesta(HttpStatus status, String mensaje) {
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON)
                .body(GenericResponse.error(mensaje));
    }
}
