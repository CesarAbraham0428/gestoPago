package com.proyecto.servicios.service.exception;

import org.springframework.http.HttpStatus;

public class CuentaNoEncontradaException extends NegocioException {
    public CuentaNoEncontradaException() { super(HttpStatus.NOT_FOUND, "Cuenta no encontrada"); }
}
