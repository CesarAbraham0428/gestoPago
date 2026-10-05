package com.proyecto.servicios.controller.advice;

import com.proyecto.servicios.model.GenericResponse;
import com.proyecto.servicios.client.GestoPagoAuthException;
import com.proyecto.servicios.client.GestoPagoCatalogException;
import com.proyecto.servicios.service.exception.AutenticacionException;
import com.proyecto.servicios.service.exception.NegocioException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import com.proyecto.servicios.service.exception.CatalogoPersistenciaException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.TransactionException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
@Slf4j
public class ApiExceptionHandler {
    @ExceptionHandler(NegocioException.class)
    ResponseEntity<GenericResponse> negocio(NegocioException exception) {
        return respuesta(exception.getStatus(), 1, exception.getMessage());
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    ResponseEntity<GenericResponse> formatoInvalido(Exception exception) {
        return respuesta(HttpStatus.BAD_REQUEST, 1, "Solicitud inválida: revisa los campos, tipos, fechas y valores ENUM permitidos");
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<GenericResponse> restriccion(DataIntegrityViolationException exception) {
        Throwable causa = exception;
        while (causa != null) {
            if (causa instanceof org.postgresql.util.PSQLException sql) {
                String constraint = sql.getServerErrorMessage() == null ? null : sql.getServerErrorMessage().getConstraint();
                if ("23505".equals(sql.getSQLState())) {
                    String mensaje = "Registro duplicado";
                    if (constraint != null && constraint.contains("curp")) mensaje = "CURP duplicada";
                    else if (constraint != null && constraint.contains("rfc")) mensaje = "RFC duplicado";
                    else if (constraint != null && constraint.contains("correo")) mensaje = "Correo electrónico duplicado";
                    return respuesta(HttpStatus.CONFLICT, 1, mensaje);
                }
                if ("23514".equals(sql.getSQLState()) || "23502".equals(sql.getSQLState())) {
                    return respuesta(HttpStatus.BAD_REQUEST, 1, "Los datos incumplen una validación del registro");
                }
            }
            causa = causa.getCause();
        }
        return respuesta(HttpStatus.CONFLICT, 1, "La operación incumple una restricción de la base de datos");
    }
    @ExceptionHandler(AutenticacionException.class)
    ResponseEntity<GenericResponse> errorAutenticacion(AutenticacionException exception) {
        return switch (exception.getTipo()) {
            case USUARIO_DUPLICADO -> respuesta(HttpStatus.CONFLICT, 1, "El usuario ya est\u00e1 registrado");
            case CREDENCIALES_INVALIDAS -> respuesta(HttpStatus.UNAUTHORIZED, 1, "Usuario o contrase\u00f1a incorrectos");
            case CUENTA_INACTIVA -> respuesta(HttpStatus.FORBIDDEN, 1, "El usuario o cliente está inactivo");
        };
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<GenericResponse> solicitudInvalida(MethodArgumentNotValidException exception) {
        String mensaje = exception.getBindingResult().getFieldErrors().stream()
                .findFirst().map(error -> error.getField() + ": " + error.getDefaultMessage())
                .orElse("Revisa los datos enviados");
        return respuesta(HttpStatus.BAD_REQUEST, 1, mensaje);
    }

    @ExceptionHandler(GestoPagoCatalogException.class)
    ResponseEntity<GenericResponse> errorCatalogoGestoPago(GestoPagoCatalogException exception) {
        return respuesta(HttpStatus.BAD_GATEWAY, 1, exception.getMessage());
    }

    @ExceptionHandler(GestoPagoAuthException.class)
    ResponseEntity<GenericResponse> errorAutenticacionGestoPago(GestoPagoAuthException exception) {
        return respuesta(HttpStatus.BAD_GATEWAY, 1, exception.getMessage());
    }

    @ExceptionHandler({DataAccessException.class, TransactionException.class, CatalogoPersistenciaException.class})
    ResponseEntity<GenericResponse> errorPersistencia(Exception exception) {
        log.error("Error no recuperable en PostgreSQL ({})", exception.getClass().getSimpleName());
        return respuesta(HttpStatus.INTERNAL_SERVER_ERROR, 2,
                "Ha ocurrido un error al consultar o guardar la información en la BD");
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<GenericResponse> errorInterno(Exception exception) {
        log.error("Error no controlado en la API ({})", exception.getClass().getSimpleName());
        return respuesta(HttpStatus.INTERNAL_SERVER_ERROR, null, "Ocurrió un error interno en la API");
    }

    private ResponseEntity<GenericResponse> respuesta(HttpStatus status, Integer codigo, String mensaje) {
        GenericResponse response = new GenericResponse();
        response.setCodigo(codigo);
        response.setMensaje(mensaje);
        return ResponseEntity.status(status).body(response);
    }
}
