package com.proyecto.servicios.service.exception;

import org.springframework.http.HttpStatus;

public class RfcDuplicadoException extends NegocioException {
    public RfcDuplicadoException() { super(HttpStatus.CONFLICT, "RFC duplicado"); }
}
