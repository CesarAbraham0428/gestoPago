package com.proyecto.servicios.config;

import com.proyecto.servicios.client.EnviadorCodigoConsola;
import com.proyecto.servicios.client.EnviadorCodigoCorreo;
import com.proyecto.servicios.service.EnviadorCodigo;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;

/**
 * Elige cómo se entrega el segundo factor. Sin SMTP configurado la API no arranca: un 2FA que no puede
 * entregar el código dejaría a todos fuera, y volver al log por omisión filtraría los códigos.
 */
@Configuration
public class EnviadorCodigoConfig {

    @Bean
    EnviadorCodigo enviadorCodigo(ObjectProvider<JavaMailSender> correo,
            @Value("${spring.mail.host:}") String host,
            @Value("${gestopago.app.2fa.remitente:}") String remitente,
            @Value("${gestopago.app.2fa.consola:false}") boolean consola) {
        if (consola) {
            return new EnviadorCodigoConsola();
        }
        JavaMailSender sender = correo.getIfAvailable();
        if (sender == null || host.isBlank() || remitente.isBlank()) {
            throw new IllegalStateException("Falta configurar el correo del segundo factor: define MAIL_HOST, "
                    + "MAIL_USERNAME, MAIL_PASSWORD y MAIL_REMITENTE (o APP_2FA_CONSOLA=true solo en desarrollo)");
        }
        return new EnviadorCodigoCorreo(sender, remitente);
    }
}
