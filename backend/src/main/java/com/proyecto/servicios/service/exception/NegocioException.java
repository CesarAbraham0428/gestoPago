package com.proyecto.servicios.service.exception;
import org.springframework.http.HttpStatus;
public class NegocioException extends RuntimeException {
    private final HttpStatus status;
    /** Segundos tras los cuales conviene reintentar (cabecera Retry-After); nulo si no aplica. */
    private final Long reintentarEnSegundos;
    public NegocioException(HttpStatus status, String message) { this(status, message, null); }
    public NegocioException(HttpStatus status, String message, Long reintentarEnSegundos) {
        super(message);
        this.status = status;
        this.reintentarEnSegundos = reintentarEnSegundos;
    }
    public HttpStatus getStatus() { return status; }
    public Long getReintentarEnSegundos() { return reintentarEnSegundos; }
}
