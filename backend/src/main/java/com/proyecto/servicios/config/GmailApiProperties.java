package com.proyecto.servicios.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Credenciales OAuth de la cuenta de Gmail que envía el código 2FA por la API HTTPS (alternativa al SMTP). */
@ConfigurationProperties(prefix = "gestopago.app.2fa.gmail")
public record GmailApiProperties(String clientId, String clientSecret, String refreshToken) {

    /** Se usa la API de Gmail solo si están las tres credenciales. */
    public boolean configurada() {
        return noVacio(clientId) && noVacio(clientSecret) && noVacio(refreshToken);
    }

    private static boolean noVacio(String valor) {
        return valor != null && !valor.isBlank();
    }
}
