package com.proyecto.servicios.service.exception;
import org.springframework.http.HttpStatus;
public class NegocioException extends RuntimeException {
    private final HttpStatus status;
    public NegocioException(HttpStatus status, String message) { super(message); this.status=status; }
    public HttpStatus getStatus() { return status; }
}
