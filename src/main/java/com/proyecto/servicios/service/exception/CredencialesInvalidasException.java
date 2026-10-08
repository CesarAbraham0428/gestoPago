package com.proyecto.servicios.service.exception;

import org.springframework.http.HttpStatus;

public class CredencialesInvalidasException extends NegocioException {
    public CredencialesInvalidasException() { super(HttpStatus.UNAUTHORIZED, "Credenciales inválidas"); }
}
