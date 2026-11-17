package com.proyecto.servicios.config;

import com.proyecto.servicios.client.EnviadorCodigoConsola;
import com.proyecto.servicios.client.EnviadorCodigoCorreo;
import com.proyecto.servicios.client.EnviadorCodigoGmailApi;
import com.proyecto.servicios.service.EnviadorCodigo;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;

/**
 * Elige cómo se entrega el segundo factor, en este orden: consola (solo desarrollo), API de Gmail (HTTPS, para
 * hosts que bloquean el SMTP) o SMTP. Sin ninguno configurado la API no arranca: un 2FA que no puede entregar el
 * código dejaría a todos fuera, y volver al log por omisión filtraría los códigos.
 */
@Configuration
@EnableConfigurationProperties(GmailApiProperties.class)
public class EnviadorCodigoConfig {

    @Bean
    EnviadorCodigo enviadorCodigo(ObjectProvider<JavaMailSender> correo,
            @Value("${spring.mail.host:}") String host,
            @Value("${gestopago.app.2fa.remitente:}") String remitente,
            @Value("${gestopago.app.2fa.consola:false}") boolean consola,
            GmailApiProperties gmail) {
        if (consola) {
            return new EnviadorCodigoConsola();
        }
        if (gmail.configurada()) {
            if (remitente.isBlank()) {
                throw new IllegalStateException("Falta MAIL_REMITENTE: debe ser la cuenta de Gmail autorizada "
                        + "para la API de Gmail");
            }
            return EnviadorCodigoGmailApi.conClienteHttpPorOmision(gmail.clientId(), gmail.clientSecret(),
                    gmail.refreshToken(), remitente);
        }
        JavaMailSender sender = correo.getIfAvailable();
        if (sender == null || host.isBlank() || remitente.isBlank()) {
            throw new IllegalStateException("Falta configurar el correo del segundo factor: define "
                    + "GMAIL_CLIENT_ID, GMAIL_CLIENT_SECRET, GMAIL_REFRESH_TOKEN y MAIL_REMITENTE (API de Gmail), "
                    + "o MAIL_HOST, MAIL_USERNAME, MAIL_PASSWORD y MAIL_REMITENTE (SMTP), "
                    + "o APP_2FA_CONSOLA=true solo en desarrollo");
        }
        return new EnviadorCodigoCorreo(sender, remitente);
    }
}
