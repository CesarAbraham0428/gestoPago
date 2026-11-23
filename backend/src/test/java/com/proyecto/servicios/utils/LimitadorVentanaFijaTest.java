package com.proyecto.servicios.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.proyecto.servicios.utils.LimitadorVentanaFija.Resultado;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

class LimitadorVentanaFijaTest {

    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> valores = mock(ValueOperations.class);
    private final AtomicLong reloj = new AtomicLong(1_000_000);
    private final LimitadorVentanaFija limitador = new LimitadorVentanaFija(redis, reloj::get);
    private static final Duration VENTANA = Duration.ofMinutes(1);

    LimitadorVentanaFijaTest() {
        when(redis.opsForValue()).thenReturn(valores);
    }

    @Test
    void springPuedeCrearElLimitadorConSuConstructorPublico() {
        // Regresión: con un segundo constructor sin @Autowired la API no arrancaba ("No default constructor").
        try (AnnotationConfigApplicationContext contexto = new AnnotationConfigApplicationContext()) {
            contexto.registerBean(StringRedisTemplate.class, () -> redis);
            contexto.register(LimitadorVentanaFija.class);
            contexto.refresh();

            assertNotNull(contexto.getBean(LimitadorVentanaFija.class));
        }
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
        when(redis.getExpire("k", TimeUnit.SECONDS)).thenReturn(25L);

        assertFalse(limitador.excede("k", 3, VENTANA));
        Resultado resultado = limitador.registrar("k", 3, VENTANA);

        assertTrue(resultado.excedido());
        assertEquals(25, resultado.reintentarEnSegundos());
        verify(redis, never()).expire("k", VENTANA);
    }

    @Test
    void unContadorSinVencimientoSeRepara() {
        when(valores.increment("k")).thenReturn(9L);
        when(redis.getExpire("k", TimeUnit.SECONDS)).thenReturn(-1L);

        Resultado resultado = limitador.registrar("k", 3, VENTANA);

        assertTrue(resultado.excedido());
        assertEquals(60, resultado.reintentarEnSegundos());
        verify(redis).expire("k", VENTANA);
    }

    @Test
    void siRedisDevuelveNuloSeCuentaEnMemoria() {
        when(valores.increment("k")).thenReturn(null);

        assertFalse(limitador.excede("k", 1, VENTANA));
        assertTrue(limitador.excede("k", 1, VENTANA));
    }

    @Test
    void siRedisFallaElLimiteSigueAplicandoseEnMemoria() {
        when(valores.increment("k")).thenThrow(new RedisConnectionFailureException("caído"));

        assertFalse(limitador.excede("k", 3, VENTANA));
        assertFalse(limitador.excede("k", 3, VENTANA));
        assertFalse(limitador.excede("k", 3, VENTANA));
        Resultado cuarta = limitador.registrar("k", 3, VENTANA);

        assertTrue(cuarta.excedido());
        assertEquals(60, cuarta.reintentarEnSegundos());
    }

    @Test
    void elRespaldoEnMemoriaSeReiniciaAlVencerLaVentanaYSeparaClaves() {
        when(valores.increment("k")).thenThrow(new RedisConnectionFailureException("caído"));
        when(valores.increment("otra")).thenThrow(new RedisConnectionFailureException("caído"));

        assertFalse(limitador.excede("k", 1, VENTANA));
        assertTrue(limitador.excede("k", 1, VENTANA));
        assertFalse(limitador.excede("otra", 1, VENTANA), "cada clave lleva su propia cuenta");

        reloj.addAndGet(VENTANA.toMillis() + 1);

        assertFalse(limitador.excede("k", 1, VENTANA), "la ventana vencida reinicia la cuenta");
    }
}
