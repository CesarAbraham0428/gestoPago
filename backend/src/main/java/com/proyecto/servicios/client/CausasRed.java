package com.proyecto.servicios.client;

import java.net.SocketTimeoutException;
import java.util.Locale;

/** Inspección de la cadena de causas de un fallo de red. */
final class CausasRed {

    private CausasRed() {
    }

    /** true si alguna causa es un timeout (por tipo, por nombre de la clase o por su mensaje). */
    static boolean contieneTimeout(Throwable e) {
        for (Throwable actual = e; actual != null; actual = actual.getCause()) {
            if (actual instanceof SocketTimeoutException
                    || actual.getClass().getSimpleName().toLowerCase(Locale.ROOT).contains("timeout")
                    || mencionaTimeout(actual.getMessage())) {
                return true;
            }
        }
        return false;
    }

    private static boolean mencionaTimeout(String mensaje) {
        if (mensaje == null) {
            return false;
        }
        String normalizado = mensaje.toLowerCase(Locale.ROOT);
        return normalizado.contains("timed out") || normalizado.contains("timeout");
    }
}
