package com.proyecto.servicios.utils;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

class LimitadorVentanaFijaTest {

    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> valores = mock(ValueOperations.class);
    private final LimitadorVentanaFija limitador = new LimitadorVentanaFija(redis);
    private static final Duration VENTANA = Duration.ofMinutes(1);

    LimitadorVentanaFijaTest() {
        when(redis.opsForValue()).thenReturn(valores);
    }

    @Test
    void laPrimeraSolicitudIniciaLaVentanaYNoExcede() {
        when(valores.increment("k")).thenReturn(1L);

        assertFalse(limitador.excede("k", 3, VENTANA));

        verify(redis).expire("k", VENTANA);
    }

    @Test
    void lasSiguientesNoReinicianLaVentanaYExcedenAlSuperarElMaximo() {
        when(valores.increment("k")).thenReturn(3L, 4L);

        assertFalse(limitador.excede("k", 3, VENTANA));
        assertTrue(limitador.excede("k", 3, VENTANA));

        verify(redis, never()).expire("k", VENTANA);
    }

    @Test
    void siRedisDevuelveNuloNoBloquea() {
        when(valores.increment("k")).thenReturn(null);

        assertFalse(limitador.excede("k", 3, VENTANA));
    }

    @Test
    void siRedisFallaDejaPasarLaSolicitud() {
        when(valores.increment("k")).thenThrow(new RedisConnectionFailureException("caído"));

        assertFalse(limitador.excede("k", 3, VENTANA));
    }
}
