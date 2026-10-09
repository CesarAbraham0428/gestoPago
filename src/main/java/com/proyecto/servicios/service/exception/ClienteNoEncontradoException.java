package com.proyecto.servicios.service.exception;

import org.springframework.http.HttpStatus;

public class ClienteNoEncontradoException extends NegocioException {
    public ClienteNoEncontradoException() { super(HttpStatus.NOT_FOUND, "Cliente no encontrado"); }
}
