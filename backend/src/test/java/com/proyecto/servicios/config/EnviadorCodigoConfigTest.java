package com.proyecto.servicios.config;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.proyecto.servicios.client.EnviadorCodigoConsola;
import com.proyecto.servicios.client.EnviadorCodigoCorreo;
import com.proyecto.servicios.client.EnviadorCodigoGmailApi;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.javamail.JavaMailSender;

class EnviadorCodigoConfigTest {

    private static final GmailApiProperties SIN_GMAIL = new GmailApiProperties("", "", "");
    private static final GmailApiProperties CON_GMAIL = new GmailApiProperties("id", "secreto", "refresco");

    private final EnviadorCodigoConfig config = new EnviadorCodigoConfig();
    @SuppressWarnings("unchecked")
    private final ObjectProvider<JavaMailSender> correo = mock(ObjectProvider.class);

    @Test
    void conModoConsolaUsaElEnviadorDeLog() {
        assertInstanceOf(EnviadorCodigoConsola.class, config.enviadorCodigo(correo, "", "", true, SIN_GMAIL));
    }

    @Test
    void conSmtpCompletoUsaElCorreo() {
        when(correo.getIfAvailable()).thenReturn(mock(JavaMailSender.class));

        assertInstanceOf(EnviadorCodigoCorreo.class, config.enviadorCodigo(correo, "smtp.local", "a@b.mx", false, SIN_GMAIL));
    }

    @Test
    void sinRemitenteHostOSenderNoArranca() {
        when(correo.getIfAvailable()).thenReturn(mock(JavaMailSender.class));
        assertThrows(IllegalStateException.class, () -> config.enviadorCodigo(correo, "", "a@b.mx", false, SIN_GMAIL));
        assertThrows(IllegalStateException.class, () -> config.enviadorCodigo(correo, "smtp.local", " ", false, SIN_GMAIL));

        when(correo.getIfAvailable()).thenReturn(null);
        assertThrows(IllegalStateException.class,
                () -> config.enviadorCodigo(correo, "smtp.local", "a@b.mx", false, SIN_GMAIL));
    }

    @Test
    void conCredencialesDeGmailUsaLaApiSinNecesitarSmtp() {
        assertInstanceOf(EnviadorCodigoGmailApi.class, config.enviadorCodigo(correo, "", "yo@gmail.com", false, CON_GMAIL));
    }

    @Test
    void laApiDeGmailTienePrioridadSobreElSmtp() {
        when(correo.getIfAvailable()).thenReturn(mock(JavaMailSender.class));

        assertInstanceOf(EnviadorCodigoGmailApi.class,
                config.enviadorCodigo(correo, "smtp.local", "yo@gmail.com", false, CON_GMAIL));
    }

    @Test
    void conGmailSinRemitenteNoArranca() {
        assertThrows(IllegalStateException.class, () -> config.enviadorCodigo(correo, "", " ", false, CON_GMAIL));
    }

    @Test
    void conCredencialesDeGmailIncompletasNoSeUsaLaApi() {
        GmailApiProperties incompletas = new GmailApiProperties("id", null, "refresco");

        assertThrows(IllegalStateException.class,
                () -> config.enviadorCodigo(correo, "", "yo@gmail.com", false, incompletas));
    }

    @Test
    void elModoConsolaTienePrioridadSobreGmail() {
        assertInstanceOf(EnviadorCodigoConsola.class, config.enviadorCodigo(correo, "", "", true, CON_GMAIL));
    }
}
