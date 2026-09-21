package com.proyecto.servicios.client;

public class GestoPagoAuthException extends RuntimeException {

    private final Tipo tipo;
    private final Integer statusHttp;

    public GestoPagoAuthException(String message, Tipo tipo, Integer statusHttp, Throwable cause) {
        super(message, cause);
        this.tipo = tipo;
        this.statusHttp = statusHttp;
    }

    public Tipo getTipo() {
        return tipo;
    }

    public Integer getStatusHttp() {
        return statusHttp;
    }

    public enum Tipo {
        AUTENTICACION,
        TIMEOUT,
        HTTP,
        COMUNICACION,
        RESPUESTA_INVALIDA
    }
}
