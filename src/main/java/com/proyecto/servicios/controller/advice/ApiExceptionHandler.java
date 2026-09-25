package com.proyecto.servicios.controller.advice;

import com.proyecto.servicios.model.GenericResponse;
import com.proyecto.servicios.client.GestoPagoAuthException;
import com.proyecto.servicios.client.GestoPagoCatalogException;
import com.proyecto.servicios.service.exception.AutenticacionException;
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
    @ExceptionHandler(AutenticacionException.class)
    ResponseEntity<GenericResponse> errorAutenticacion(AutenticacionException exception) {
        return switch (exception.getTipo()) {
            case USUARIO_DUPLICADO -> respuesta(HttpStatus.CONFLICT, 1, "El usuario ya est\u00e1 registrado");
            case CREDENCIALES_INVALIDAS -> respuesta(HttpStatus.UNAUTHORIZED, 1, "Usuario o contrase\u00f1a incorrectos");
            case CUENTA_INACTIVA -> respuesta(HttpStatus.FORBIDDEN, 1, "La cuenta est\u00e1 inactiva");
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
        log.error("Error no recuperable del catálogo GestoPago: tipo={}, statusHttp={}",
                exception.getTipo(), exception.getStatusHttp());
        return respuesta(HttpStatus.BAD_GATEWAY, 1, mensajeCatalogo(exception.getTipo()));
    }

    @ExceptionHandler(GestoPagoAuthException.class)
    ResponseEntity<GenericResponse> errorAutenticacionGestoPago(GestoPagoAuthException exception) {
        log.error("Error no recuperable al autenticar con GestoPago: tipo={}, statusHttp={}",
                exception.getTipo(), exception.getStatusHttp());
        return respuesta(HttpStatus.BAD_GATEWAY, 1, mensajeAutenticacion(exception.getTipo()));
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

    private String mensajeCatalogo(GestoPagoCatalogException.Tipo tipo) {
        return switch (tipo) {
            case AUTENTICACION -> "GestoPago rechazó la autenticación del catálogo";
            case TIMEOUT -> "Se agotó el tiempo de espera al consultar GestoPago";
            case HTTP -> "GestoPago devolvió una respuesta HTTP no exitosa";
            case COMUNICACION -> "No hay conexión al servicio de GestoPago";
            case XML -> "La respuesta XML de GestoPago no es válida";
            case RESPUESTA_VACIA -> "GestoPago devolvió una respuesta vacía";
            case RESPUESTA_INVALIDA -> "GestoPago no devolvió un catálogo válido";
        };
    }

    private String mensajeAutenticacion(GestoPagoAuthException.Tipo tipo) {
        return switch (tipo) {
            case AUTENTICACION -> "GestoPago rechazó las credenciales de autenticación";
            case TIMEOUT -> "Se agotó el tiempo de espera al autenticar con GestoPago";
            case HTTP -> "GestoPago devolvió un error HTTP durante la autenticación";
            case COMUNICACION -> "No hay conexión al servicio de autenticación de GestoPago";
            case RESPUESTA_INVALIDA -> "GestoPago no devolvió un token válido";
        };
    }

    private ResponseEntity<GenericResponse> respuesta(HttpStatus status, Integer codigo, String mensaje) {
        GenericResponse response = new GenericResponse();
        response.setCodigo(codigo);
        response.setMensaje(mensaje);
        return ResponseEntity.status(status).body(response);
    }
}
