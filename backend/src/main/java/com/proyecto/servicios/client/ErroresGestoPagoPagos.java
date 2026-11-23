package com.proyecto.servicios.client;

import feign.FeignException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.Locale;

/** Clasifica los fallos de la comunicación con GestoPago y decide si el resultado de la operación es incierto. */
final class ErroresGestoPagoPagos {

    private ErroresGestoPagoPagos() {
    }

    static GestoPagoPagoException traducirFeign(FeignException e) {
        if (e.status() >= 0) {
            return errorHttp(e.status(), e);
        }
        // Si ni siquiera se pudo conectar, GestoPago no recibió la petición y es seguro devolver el saldo.
        if (sinConexion(e)) {
            return new GestoPagoPagoException("No se pudo establecer comunicación con GestoPago",
                    GestoPagoPagoException.Tipo.COMUNICACION, null, false, e);
        }
        boolean timeout = CausasRed.contieneTimeout(e);
        return new GestoPagoPagoException(
                timeout ? "Se agotó el tiempo de espera de la respuesta de GestoPago"
                        : "Se interrumpió la comunicación con GestoPago",
                timeout ? GestoPagoPagoException.Tipo.TIMEOUT : GestoPagoPagoException.Tipo.COMUNICACION,
                null, true, e);
    }

    static GestoPagoPagoException errorHttp(int status, Throwable e) {
        if (status == 401 || status == 403) {
            return new GestoPagoPagoException("GestoPago rechazó la autenticación del pago",
                    GestoPagoPagoException.Tipo.AUTENTICACION, status, false, e);
        }
        // 4xx: la petición fue rechazada antes de procesarse. 5xx/408: pudo procesarse.
        boolean incierto = status >= 500 || status == 408;
        return new GestoPagoPagoException("GestoPago devolvió una respuesta HTTP no exitosa",
                GestoPagoPagoException.Tipo.HTTP, status, incierto, e);
    }

    static GestoPagoPagoException respuestaInvalida(String mensaje, Integer status, Throwable e) {
        return new GestoPagoPagoException(mensaje, GestoPagoPagoException.Tipo.RESPUESTA_INVALIDA, status, true, e);
    }

    private static boolean sinConexion(Throwable e) {
        for (Throwable actual = e; actual != null; actual = actual.getCause()) {
            if (actual instanceof ConnectException || actual instanceof UnknownHostException
                    || (actual instanceof SocketTimeoutException && mencionaConexion(actual))) {
                return true;
            }
        }
        return false;
    }

    private static boolean mencionaConexion(Throwable e) {
        return e.getMessage() != null && e.getMessage().toLowerCase(Locale.ROOT).contains("connect");
    }
}
