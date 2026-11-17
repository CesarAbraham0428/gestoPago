package com.proyecto.servicios.client;

import com.proyecto.servicios.service.EnviadorCodigo;
import java.time.Duration;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

/** Entrega el código por correo electrónico (SMTP configurado con spring.mail.*). */
public class EnviadorCodigoCorreo implements EnviadorCodigo {

    private final JavaMailSender correo;
    private final String remitente;

    public EnviadorCodigoCorreo(JavaMailSender correo, String remitente) {
        this.correo = correo;
        this.remitente = remitente;
    }

    @Override
    public void enviar(String destinatario, String codigo, Duration vigencia) {
        SimpleMailMessage mensaje = new SimpleMailMessage();
        mensaje.setFrom(remitente);
        mensaje.setTo(destinatario);
        mensaje.setSubject("Tu código de verificación de GestoPago");
        mensaje.setText("Tu código de verificación es: " + codigo + "\n\n"
                + "Vence en " + vigencia.toMinutes() + " minutos y solo sirve una vez.\n"
                + "Si no intentaste iniciar sesión, ignora este mensaje y cambia tu contraseña.");
        correo.send(mensaje);
    }
}
