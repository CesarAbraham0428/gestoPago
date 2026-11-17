package com.proyecto.servicios.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class JwtTokenServiceBordesTest {

    private static final String SECRETO = "una-llave-de-prueba-de-al-menos-32-bytes!!";

    @Test
    void exponeLaDuracionConfigurada() {
        assertEquals(60_000L, new JwtTokenService(SECRETO, 60_000L).getExpirationMs());
    }

    @Test
    void rechazaSecretosNulosOCortosYDuracionesNoPositivas() {
        assertThrows(IllegalArgumentException.class, () -> new JwtTokenService(null, 1000L));
        assertThrows(IllegalArgumentException.class, () -> new JwtTokenService("corto", 1000L));
        assertThrows(IllegalArgumentException.class, () -> new JwtTokenService(SECRETO, 0L));
        assertThrows(IllegalArgumentException.class, () -> new JwtTokenService(SECRETO, -5L));
    }
}
