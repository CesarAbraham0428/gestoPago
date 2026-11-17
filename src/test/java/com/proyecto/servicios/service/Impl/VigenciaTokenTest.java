package com.proyecto.servicios.service.Impl;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.proyecto.servicios.entity.gestopago.GestoPagoToken;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class VigenciaTokenTest {

    private static final Clock RELOJ = Clock.fixed(Instant.parse("2026-11-01T12:00:00Z"), ZoneOffset.UTC);
    private static final LocalDateTime AHORA = LocalDateTime.now(RELOJ);
    private final VigenciaToken vigencia = new VigenciaToken(3_600_000L, RELOJ);

    private static GestoPagoToken token(String valor, LocalDateTime emision, Long expiraEnSegundos) {
        GestoPagoToken token = new GestoPagoToken();
        token.setToken(valor);
        token.setFechaActualizacion(emision);
        token.setExpiresIn(expiraEnSegundos);
        return token;
    }

    @Test
    void sinContenidoSiempreSeRenuevaYNuncaSeConsideraVigente() {
        for (String valor : new String[] {null, "", "  "}) {
            GestoPagoToken token = token(valor, AHORA, 3600L);

            assertTrue(vigencia.necesitaRenovacion(token));
            assertFalse(vigencia.sigueVigente(token));
        }
    }

    @Test
    void unTokenFrescoNoSeRenueva() {
        GestoPagoToken token = token("t", AHORA.minusMinutes(5), 3600L);

        assertFalse(vigencia.necesitaRenovacion(token));
        assertTrue(vigencia.sigueVigente(token));
    }

    @Test
    void unTokenAMenosDeUnMinutoDeVencerSeRenuevaPeroSigueVigente() {
        GestoPagoToken token = token("t", AHORA.minusSeconds(3600 - 30), 3600L);

        assertTrue(vigencia.necesitaRenovacion(token));
        assertTrue(vigencia.sigueVigente(token));
    }

    @Test
    void unTokenVencidoYaNoEsVigente() {
        GestoPagoToken token = token("t", AHORA.minusHours(2), 3600L);

        assertTrue(vigencia.necesitaRenovacion(token));
        assertFalse(vigencia.sigueVigente(token));
    }

    @Test
    void unTokenDeVidaCortaUsaUnDecimoDeSuDuracionComoMargen() {
        GestoPagoToken token = token("t", AHORA.minusSeconds(10), 30L);

        assertFalse(vigencia.necesitaRenovacion(token));
        assertTrue(vigencia.necesitaRenovacion(token("t", AHORA.minusSeconds(28), 30L)));
    }

    @Test
    void sinDuracionPropiaSeUsaLaConfigurada() {
        assertFalse(vigencia.necesitaRenovacion(token("t", AHORA.minusMinutes(10), null)));
        assertTrue(vigencia.necesitaRenovacion(token("t", AHORA.minusHours(2), 0L)));
        assertTrue(new VigenciaToken(0L, RELOJ).necesitaRenovacion(token("t", AHORA.minusSeconds(1), null)));
    }

    @Test
    void laFechaDeEmisionCaeEnLaCreacionOEnElMinimo() {
        GestoPagoToken soloCreacion = token("t", null, 3600L);
        soloCreacion.setFechaCreacion(AHORA.minusMinutes(1));
        assertFalse(vigencia.necesitaRenovacion(soloCreacion));

        GestoPagoToken sinFechas = token("t", null, 3600L);
        assertTrue(vigencia.necesitaRenovacion(sinFechas));
        assertFalse(vigencia.sigueVigente(sinFechas));
    }
}
