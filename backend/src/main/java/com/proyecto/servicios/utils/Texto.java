package com.proyecto.servicios.utils;

import java.util.Locale;

/** Normalización de texto de entrada (identidades, correos y opcionales). */
public final class Texto {

    private Texto() {
    }

    /** RFC y CURP se comparan siempre en mayúsculas y sin espacios en los extremos. */
    public static String identidad(String valor) {
        return valor.trim().toUpperCase(Locale.ROOT);
    }

    public static String correo(String valor) {
        return valor.trim().toLowerCase(Locale.ROOT);
    }

    /** Recorta un texto opcional; conserva el nulo. */
    public static String opcional(String valor) {
        return valor == null ? null : valor.trim();
    }
}
