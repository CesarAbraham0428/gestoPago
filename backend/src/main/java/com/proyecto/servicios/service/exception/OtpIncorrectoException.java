package com.proyecto.servicios.service.exception;
import org.springframework.http.HttpStatus;
/**
 * 403 y no 401: el cliente trata el 401 como sesión vencida. Las transacciones no se revierten con esta
 * excepción: el intento fallido debe quedar registrado.
 */
public class OtpIncorrectoException extends NegocioException {
    public OtpIncorrectoException(String message) { super(HttpStatus.FORBIDDEN, message); }
}
