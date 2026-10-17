package com.proyecto.servicios.service.exception;

import org.springframework.http.HttpStatus;

public class UsuarioNoEncontradoException extends NegocioException {
    public UsuarioNoEncontradoException() { super(HttpStatus.NOT_FOUND, "Usuario no encontrado"); }
}
