package com.proyecto.servicios.service.exception;

import org.springframework.http.HttpStatus;

public class CurpDuplicadaException extends NegocioException {
    public CurpDuplicadaException() { super(HttpStatus.CONFLICT, "CURP duplicada"); }
}
