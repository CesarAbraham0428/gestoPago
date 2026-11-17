package com.proyecto.servicios.config;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.proyecto.servicios.client.EnviadorCodigoConsola;
import com.proyecto.servicios.client.EnviadorCodigoCorreo;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.javamail.JavaMailSender;

class EnviadorCodigoConfigTest {

    private final EnviadorCodigoConfig config = new EnviadorCodigoConfig();
    @SuppressWarnings("unchecked")
    private final ObjectProvider<JavaMailSender> correo = mock(ObjectProvider.class);

    @Test
    void conModoConsolaUsaElEnviadorDeLog() {
        assertInstanceOf(EnviadorCodigoConsola.class, config.enviadorCodigo(correo, "", "", true));
    }

    @Test
    void conSmtpCompletoUsaElCorreo() {
        when(correo.getIfAvailable()).thenReturn(mock(JavaMailSender.class));

        assertInstanceOf(EnviadorCodigoCorreo.class, config.enviadorCodigo(correo, "smtp.local", "a@b.mx", false));
    }

    @Test
    void sinRemitenteHostOSenderNoArranca() {
        when(correo.getIfAvailable()).thenReturn(mock(JavaMailSender.class));
        assertThrows(IllegalStateException.class, () -> config.enviadorCodigo(correo, "", "a@b.mx", false));
        assertThrows(IllegalStateException.class, () -> config.enviadorCodigo(correo, "smtp.local", " ", false));

        when(correo.getIfAvailable()).thenReturn(null);
        assertThrows(IllegalStateException.class,
                () -> config.enviadorCodigo(correo, "smtp.local", "a@b.mx", false));
    }
}
