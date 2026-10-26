package com.proyecto.servicios.client;

public class GestoPagoPagoException extends RuntimeException {

    private final Tipo tipo;
    private final Integer statusHttp;
    private final boolean resultadoIncierto;

    public GestoPagoPagoException(String message, Tipo tipo, Integer statusHttp,
            boolean resultadoIncierto, Throwable cause) {
        super(message, cause);
        this.tipo = tipo;
        this.statusHttp = statusHttp;
        this.resultadoIncierto = resultadoIncierto;
    }

    public Tipo getTipo() {
        return tipo;
    }

    public Integer getStatusHttp() {
        return statusHttp;
    }

    /**
     * true cuando la petición pudo llegar a GestoPago y no sabemos si aplicó el pago
     * (timeout de lectura, HTTP 5xx, respuesta ilegible). En ese caso el saldo no se devuelve.
     */
    public boolean isResultadoIncierto() {
        return resultadoIncierto;
    }

    public enum Tipo {
        AUTENTICACION,
        TIMEOUT,
        HTTP,
        COMUNICACION,
        RESPUESTA_INVALIDA
    }
}
