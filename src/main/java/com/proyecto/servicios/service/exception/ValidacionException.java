package com.proyecto.servicios.service.exception;

import org.springframework.http.HttpStatus;

public class ValidacionException extends NegocioException {
    public ValidacionException(String mensaje) { super(HttpStatus.BAD_REQUEST, mensaje); }
}
