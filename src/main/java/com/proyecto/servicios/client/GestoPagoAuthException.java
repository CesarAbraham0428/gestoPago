package com.proyecto.servicios.client;

import feign.FeignException;

public class GestoPagoAuthException extends RuntimeException {

    private final Tipo tipo;
    private final Integer statusHttp;

    public GestoPagoAuthException(String message, Tipo tipo, Integer statusHttp, Throwable cause) {
        super(message, cause);
        this.tipo = tipo;
        this.statusHttp = statusHttp;
    }

    /** Clasifica un fallo de Feign al autenticar: credenciales rechazadas, HTTP, timeout o comunicación. */
    public static GestoPagoAuthException desde(FeignException exception) {
        Integer status = exception.status() >= 0 ? exception.status() : null;
        if (status != null && (status == 401 || status == 403)) {
            return new GestoPagoAuthException("GestoPago rechazó las credenciales de autenticación",
                    Tipo.AUTENTICACION, status, exception);
        }
        if (status != null) {
            return new GestoPagoAuthException("GestoPago devolvió un estado HTTP no exitoso al autenticar",
                    Tipo.HTTP, status, exception);
        }
        if (CausasRed.contieneTimeout(exception)) {
            return new GestoPagoAuthException("Se agotó el tiempo de espera al autenticar con GestoPago",
                    Tipo.TIMEOUT, null, exception);
        }
        return new GestoPagoAuthException(
                "No se pudo establecer comunicación con el servicio de autenticación de GestoPago",
                Tipo.COMUNICACION, null, exception);
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
