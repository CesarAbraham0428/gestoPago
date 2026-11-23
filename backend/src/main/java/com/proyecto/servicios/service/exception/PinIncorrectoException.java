package com.proyecto.servicios.service.exception;
import org.springframework.http.HttpStatus;
/** Las transacciones no se revierten con esta excepción: el intento fallido debe quedar registrado. */
public class PinIncorrectoException extends NegocioException {
    public PinIncorrectoException(String message) { super(HttpStatus.FORBIDDEN, message); }
}
