package com.proyecto.servicios.security;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import static org.junit.jupiter.api.Assertions.*;

class JwtTokenServiceTest {
    private static final String SECRET = "test-only-secret-012345678901234567890";
    private final JwtTokenService tokens = new JwtTokenService(SECRET, 60000);

    @Test void tokenFirmadoConIdentidadYVigencia() {
        var jwt = tokens.validar(tokens.emitir("7"));
        assertEquals("7", jwt.getSubject());
        assertEquals("gestopago-api", jwt.getIssuer());
        assertEquals(60000, jwt.getExpiresAt().getTime() - jwt.getIssuedAt().getTime());
    }

    @Test void rechazaFirmaAjenaTokenVencidoYEmisorAjeno() {
        String ajeno = new JwtTokenService("another-secret-012345678901234567890", 60000).emitir("7");
        assertThrows(JWTVerificationException.class, () -> tokens.validar(ajeno));
        String vencido = JWT.create().withIssuer("gestopago-api").withSubject("7")
            .withExpiresAt(Instant.now().minusSeconds(60)).sign(Algorithm.HMAC256(SECRET));
        assertThrows(JWTVerificationException.class, () -> tokens.validar(vencido));
        String emisorAjeno = JWT.create().withIssuer("otro").sign(Algorithm.HMAC256(SECRET));
        assertThrows(JWTVerificationException.class, () -> tokens.validar(emisorAjeno));
    }

    @Test void rechazaConfiguracionInvalida() {
        assertThrows(IllegalArgumentException.class, () -> new JwtTokenService("corta", 1000));
        assertThrows(IllegalArgumentException.class, () -> new JwtTokenService(SECRET, 0));
    }
}
