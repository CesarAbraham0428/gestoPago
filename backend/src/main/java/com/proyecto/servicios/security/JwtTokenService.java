package com.proyecto.servicios.security;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Date;
import java.util.UUID;

@Service
public class JwtTokenService {
    private static final String ISSUER = "gestopago-api";

    private final Algorithm algorithm;
    private final long expirationMs;

    public JwtTokenService(
            @Value("${gestopago.app.jwt.secret}") String secret,
            @Value("${gestopago.app.jwt.expiration-ms:28800000}") long expirationMs) {
        if (secret == null || secret.getBytes(java.nio.charset.StandardCharsets.UTF_8).length < 32) {
            throw new IllegalArgumentException("APP_JWT_SECRET debe tener al menos 32 bytes");
        }
        this.algorithm = Algorithm.HMAC256(secret);
        if (expirationMs <= 0) {
            throw new IllegalArgumentException("La duración del JWT debe ser positiva");
        }
        this.expirationMs = expirationMs;
    }

    /** Cada token lleva un identificador único (jti) que lo liga a la sesión registrada en el servidor. */
    public TokenEmitido emitir(String usuario) {
        Instant ahora = Instant.now();
        Instant expira = ahora.plusMillis(expirationMs);
        String jti = UUID.randomUUID().toString();
        String token = JWT.create()
                .withIssuer(ISSUER)
                .withSubject(usuario)
                .withJWTId(jti)
                .withIssuedAt(Date.from(ahora))
                .withExpiresAt(Date.from(expira))
                .sign(algorithm);
        return new TokenEmitido(token, jti, expira);
    }

    public record TokenEmitido(String token, String jti, Instant expiraEn) {}

    public DecodedJWT validar(String token) {
        return JWT.require(algorithm).withIssuer(ISSUER).build().verify(token);
    }

    public long getExpirationMs() {
        return expirationMs;
    }
}
