package com.proyecto.servicios.controller;

import com.proyecto.servicios.model.GenericResponse;
import com.proyecto.servicios.service.AutenticacionService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(AutenticacionService.UsuarioDuplicadoException.class)
    ResponseEntity<GenericResponse> usuarioDuplicado() {
        return respuesta(HttpStatus.CONFLICT, 1, "El usuario ya está registrado");
    }

    @ExceptionHandler(AutenticacionService.CredencialesInvalidasException.class)
    ResponseEntity<GenericResponse> credencialesInvalidas() {
        return respuesta(HttpStatus.UNAUTHORIZED, 1, "Usuario o contraseña incorrectos");
    }

    @ExceptionHandler(AutenticacionService.CuentaInactivaException.class)
    ResponseEntity<GenericResponse> cuentaInactiva() {
        return respuesta(HttpStatus.FORBIDDEN, 1, "La cuenta está inactiva");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<GenericResponse> solicitudInvalida(MethodArgumentNotValidException exception) {
        String mensaje = exception.getBindingResult().getFieldErrors().stream()
                .findFirst().map(error -> error.getField() + ": " + error.getDefaultMessage())
                .orElse("Revisa los datos enviados");
        return respuesta(HttpStatus.BAD_REQUEST, 1, mensaje);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<GenericResponse> conflictoDeDatos() {
        return respuesta(HttpStatus.CONFLICT, 1, "El usuario ya está registrado");
    }

    private ResponseEntity<GenericResponse> respuesta(HttpStatus status, Integer codigo, String mensaje) {
        GenericResponse response = new GenericResponse();
        response.setCodigo(codigo);
        response.setMensaje(mensaje);
        return ResponseEntity.status(status).body(response);
    }
}
