package com.proyecto.servicios.service.Impl;

import com.proyecto.servicios.entity.gestopago.GestoPagoToken;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;

/** Política de vigencia del token de GestoPago: cuándo hay que renovarlo y cuándo todavía sirve. */
class VigenciaToken {

    private static final Duration MARGEN_RENOVACION = Duration.ofMinutes(1);

    private final long duracionPorOmisionMs;
    private final Clock clock;

    VigenciaToken(long duracionPorOmisionMs, Clock clock) {
        this.duracionPorOmisionMs = duracionPorOmisionMs;
        this.clock = clock;
    }

    /** Se renueva un poco antes de vencer (o de inmediato si no hay token utilizable). */
    boolean necesitaRenovacion(GestoPagoToken token) {
        if (!tieneContenido(token)) {
            return true;
        }
        Duration duracion = duracion(token);
        Duration margen = duracion.compareTo(MARGEN_RENOVACION) > 0 ? MARGEN_RENOVACION : duracion.dividedBy(10);
        return !vence(token).isAfter(ahora().plus(margen));
    }

    boolean sigueVigente(GestoPagoToken token) {
        return tieneContenido(token) && vence(token).isAfter(ahora());
    }

    private boolean tieneContenido(GestoPagoToken token) {
        return token.getToken() != null && !token.getToken().isBlank();
    }

    private LocalDateTime ahora() {
        return LocalDateTime.now(clock);
    }

    private LocalDateTime vence(GestoPagoToken token) {
        return emision(token).plus(duracion(token));
    }

    private LocalDateTime emision(GestoPagoToken token) {
        if (token.getFechaActualizacion() != null) {
            return token.getFechaActualizacion();
        }
        return token.getFechaCreacion() != null ? token.getFechaCreacion() : LocalDateTime.MIN;
    }

    private Duration duracion(GestoPagoToken token) {
        if (token.getExpiresIn() != null && token.getExpiresIn() > 0) {
            return Duration.ofSeconds(token.getExpiresIn());
        }
        return Duration.ofMillis(Math.max(duracionPorOmisionMs, 1));
    }
}
