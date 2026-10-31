package com.proyecto.servicios.service;

import com.proyecto.servicios.service.exception.NegocioException;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.HttpStatus;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class LimitePagosServiceTest {

    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> valores = mock(ValueOperations.class);
    private final LimitePagosService service = new LimitePagosService(redis, 2);

    @BeforeEach
    void preparar() {
        when(redis.opsForValue()).thenReturn(valores);
    }

    @Test
    void primeraSolicitudIniciaLaVentana() {
        when(valores.increment("limite:pagos:7")).thenReturn(1L);

        service.verificar(7);

        verify(redis).expire("limite:pagos:7", Duration.ofMinutes(1));
    }

    @Test
    void superarElLimiteResponde429() {
        when(valores.increment("limite:pagos:7")).thenReturn(3L);

        NegocioException error = assertThrows(NegocioException.class, () -> service.verificar(7));

        assertEquals(HttpStatus.TOO_MANY_REQUESTS, error.getStatus());
        verify(redis, never()).expire(any(), any(Duration.class));
    }

    @Test
    void redisCaidoNoDetieneLosPagos() {
        when(valores.increment("limite:pagos:7")).thenThrow(new RedisConnectionFailureException("sin conexión"));

        assertDoesNotThrow(() -> service.verificar(7));
    }
}
