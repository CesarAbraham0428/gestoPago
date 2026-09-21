package com.proyecto.servicios.client;

public class GestoPagoCatalogException extends RuntimeException {

    private final Tipo tipo;
    private final Integer statusHttp;

    public GestoPagoCatalogException(String message) {
        this(message, Tipo.RESPUESTA_INVALIDA, null, null);
    }

    public GestoPagoCatalogException(String message, Throwable cause) {
        this(message, Tipo.COMUNICACION, null, cause);
    }

    public GestoPagoCatalogException(String message, Tipo tipo, Throwable cause) {
        this(message, tipo, null, cause);
    }

    public GestoPagoCatalogException(String message, Tipo tipo, Integer statusHttp, Throwable cause) {
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
        XML,
        RESPUESTA_VACIA,
        RESPUESTA_INVALIDA
    }
}
