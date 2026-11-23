package com.proyecto.servicios.service.exception;

import org.springframework.http.HttpStatus;

public class UsuarioInactivoException extends NegocioException {
    public UsuarioInactivoException() { super(HttpStatus.FORBIDDEN, "Usuario inactivo"); }
}
