package com.proyecto.servicios.client;

import java.time.Duration;

/** Asunto y cuerpo del correo con el código de verificación (compartidos por los enviadores de correo). */
final class TextoCodigo {

    static final String ASUNTO = "Tu código de verificación de GestoPago";

    private TextoCodigo() {
    }

    static String cuerpo(String codigo, Duration vigencia) {
        return "Tu código de verificación es: " + codigo + "\n\n"
                + "Vence en " + vigencia.toMinutes() + " minutos y solo sirve una vez.\n"
                + "Si no intentaste iniciar sesión, ignora este mensaje y cambia tu contraseña.";
    }
}
