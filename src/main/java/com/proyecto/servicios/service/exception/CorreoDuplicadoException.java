package com.proyecto.servicios.service.exception;

import org.springframework.http.HttpStatus;

public class CorreoDuplicadoException extends NegocioException {
    public CorreoDuplicadoException() { super(HttpStatus.CONFLICT, "Correo electrónico duplicado"); }
}
