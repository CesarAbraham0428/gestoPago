package com.proyecto.servicios.integration;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.springframework.stereotype.Component;

/** Reloj que las pruebas de integración avanzan a mano: cada código TOTP solo sirve una vez por paso de 30 s. */
@Component
public class RelojDePrueba extends Clock {
    private volatile Instant ahora = Instant.now();

    public void avanzarUnPaso() {
        ahora = ahora.plusSeconds(30);
    }

    @Override
    public ZoneId getZone() {
        return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return this;
    }

    @Override
    public Instant instant() {
        return ahora;
    }
}
