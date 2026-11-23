package com.proyecto.servicios.utils;

import java.security.SecureRandom;

public final class CadenaAleatoria {

    private static final String ALFABETO = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    private CadenaAleatoria() {
    }

    /** Cadena alfanumérica en mayúsculas; con 15 caracteres hay 36^15 combinaciones posibles. */
    public static String generar(int longitud) {
        StringBuilder cadena = new StringBuilder(longitud);
        for (int i = 0; i < longitud; i++) {
            cadena.append(ALFABETO.charAt(RANDOM.nextInt(ALFABETO.length())));
        }
        return cadena.toString();
    }
}
