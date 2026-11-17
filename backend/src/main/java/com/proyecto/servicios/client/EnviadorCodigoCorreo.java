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
        mensaje.setSubject(TextoCodigo.ASUNTO);
        mensaje.setText(TextoCodigo.cuerpo(codigo, vigencia));
        correo.send(mensaje);
    }
}
