package com.proyecto.servicios.security;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Date;

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
        this.expirationMs = expirationMs;
    }

    public String emitir(String usuario) {
        Instant ahora = Instant.now();
        return JWT.create()
                .withIssuer(ISSUER)
                .withSubject(usuario)
                .withIssuedAt(Date.from(ahora))
                .withExpiresAt(Date.from(ahora.plusMillis(expirationMs)))
                .sign(algorithm);
    }

    public DecodedJWT validar(String token) {
        return JWT.require(algorithm).withIssuer(ISSUER).build().verify(token);
    }

    public long getExpirationMs() {
        return expirationMs;
    }
}
