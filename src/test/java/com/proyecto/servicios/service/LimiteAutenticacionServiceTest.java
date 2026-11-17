package com.proyecto.servicios.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.proyecto.servicios.service.exception.NegocioException;
import com.proyecto.servicios.utils.LimitadorVentanaFija;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class LimiteAutenticacionServiceTest {

    private final LimitadorVentanaFija limitador = mock(LimitadorVentanaFija.class);
    private final LimiteAutenticacionService servicio = new LimiteAutenticacionService(limitador, 10, 40, 7);

    @Test
    void elInicioDeSesionSeLimitaPorIpYPorCorreoNormalizado() {
        servicio.verificarInicioDeSesion("1.2.3.4", "  Ana@Correo.MX ");

        verify(limitador).excede(eq("limite:login:ip:1.2.3.4"), eq(40), any(Duration.class));
        verify(limitador).excede(eq("limite:login:correo:ana@correo.mx"), eq(10), eq(Duration.ofMinutes(15)));
    }

    @Test
    void sinCorreoSoloSeLimitaPorIp() {
        assertDoesNotThrow(() -> servicio.verificarInicioDeSesion("1.2.3.4", null));

        verify(limitador).excede(anyString(), eq(40), any(Duration.class));
    }

    @Test
    void excederCualquieraDeLosLimitesRespondeTooManyRequests() {
        when(limitador.excede(eq("limite:login:ip:1.2.3.4"), anyInt(), any())).thenReturn(true);
        when(limitador.excede(eq("limite:login:correo:a@b.mx"), anyInt(), any())).thenReturn(false);

        NegocioException error = assertThrows(NegocioException.class,
                () -> servicio.verificarInicioDeSesion("1.2.3.4", "a@b.mx"));

        assertEquals(HttpStatus.TOO_MANY_REQUESTS, error.getStatus());
    }

    @Test
    void elLimiteDelCorreoTambienBloquea() {
        when(limitador.excede(eq("limite:login:correo:a@b.mx"), anyInt(), any())).thenReturn(true);

        assertThrows(NegocioException.class, () -> servicio.verificarInicioDeSesion("1.2.3.4", "a@b.mx"));
    }

    @Test
    void elRegistroSeLimitaPorIp() {
        assertDoesNotThrow(() -> servicio.verificarRegistro("1.2.3.4"));
        verify(limitador).excede("limite:registro:ip:1.2.3.4", 7, Duration.ofHours(1));

        when(limitador.excede(anyString(), anyInt(), any())).thenReturn(true);
        assertThrows(NegocioException.class, () -> servicio.verificarRegistro("1.2.3.4"));
    }
}
