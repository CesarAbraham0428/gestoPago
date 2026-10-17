package com.proyecto.servicios.service.exception;

import org.springframework.http.HttpStatus;

public class ClienteYaRegistradoException extends NegocioException {
    public ClienteYaRegistradoException() { this("El cliente ya está registrado"); }
    protected ClienteYaRegistradoException(String mensaje) { super(HttpStatus.CONFLICT, mensaje); }
}
