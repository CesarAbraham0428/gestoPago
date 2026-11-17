package com.proyecto.servicios.client;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

class EnviadoresCodigoTest {

    @Test
    void elCorreoLlevaElCodigoYSuVigencia() {
        JavaMailSender sender = mock(JavaMailSender.class);

        new EnviadorCodigoCorreo(sender, "noreply@gestopago.mx")
                .enviar("ana@correo.mx", "123456", Duration.ofMinutes(5));

        ArgumentCaptor<SimpleMailMessage> enviado = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(sender).send(enviado.capture());
        assertEquals("noreply@gestopago.mx", enviado.getValue().getFrom());
        assertEquals("ana@correo.mx", enviado.getValue().getTo()[0]);
        assertTrue(enviado.getValue().getText().contains("123456"));
        assertTrue(enviado.getValue().getText().contains("5 minutos"));
    }

    @Test
    void elEnviadorDeConsolaNoLanzaExcepciones() {
        EnviadorCodigoConsola enviador = new EnviadorCodigoConsola();

        assertDoesNotThrow(() -> enviador.enviar("ana@correo.mx", "123456", Duration.ofMinutes(5)));
    }
}
