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
import com.proyecto.servicios.utils.LimitadorVentanaFija.Resultado;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class LimiteAutenticacionServiceTest {

    private static final Resultado EXCEDIDO = new Resultado(true, 120);

    private final LimitadorVentanaFija limitador = mock(LimitadorVentanaFija.class);
    private final LimiteAutenticacionService servicio = new LimiteAutenticacionService(limitador, 10, 40, 7, 30);

    @BeforeEach
    void sinExceso() {
        when(limitador.registrar(anyString(), anyInt(), any())).thenReturn(Resultado.PERMITIDO);
    }

    @Test
    void elInicioDeSesionSeLimitaPorIpYPorCorreoNormalizado() {
        servicio.verificarInicioDeSesion("1.2.3.4", "  Ana@Correo.MX ");

        verify(limitador).registrar(eq("limite:login:ip:1.2.3.4"), eq(40), any(Duration.class));
        verify(limitador).registrar(eq("limite:login:correo:ana@correo.mx"), eq(10), eq(Duration.ofMinutes(15)));
    }

    @Test
    void sinCorreoSoloSeLimitaPorIp() {
        assertDoesNotThrow(() -> servicio.verificarInicioDeSesion("1.2.3.4", null));

        verify(limitador).registrar(anyString(), eq(40), any(Duration.class));
    }

    @Test
    void excederCualquieraDeLosLimitesRespondeTooManyRequests() {
        when(limitador.registrar(eq("limite:login:ip:1.2.3.4"), anyInt(), any())).thenReturn(EXCEDIDO);

        NegocioException error = assertThrows(NegocioException.class,
                () -> servicio.verificarInicioDeSesion("1.2.3.4", "a@b.mx"));

        assertEquals(HttpStatus.TOO_MANY_REQUESTS, error.getStatus());
        assertEquals(120L, error.getReintentarEnSegundos());
    }

    @Test
    void elLimiteDelCorreoTambienBloquea() {
        when(limitador.registrar(eq("limite:login:correo:a@b.mx"), anyInt(), any())).thenReturn(EXCEDIDO);

        assertThrows(NegocioException.class, () -> servicio.verificarInicioDeSesion("1.2.3.4", "a@b.mx"));
    }

    @Test
    void elRechazoInformaElMayorTiempoDeEsperaParaReintentar() {
        when(limitador.registrar(eq("limite:login:ip:1.2.3.4"), anyInt(), any())).thenReturn(new Resultado(true, 30));
        when(limitador.registrar(eq("limite:login:correo:a@b.mx"), anyInt(), any()))
                .thenReturn(new Resultado(true, 600));

        NegocioException error = assertThrows(NegocioException.class,
                () -> servicio.verificarInicioDeSesion("1.2.3.4", "a@b.mx"));

        assertEquals(600L, error.getReintentarEnSegundos());
    }

    @Test
    void elRegistroSeLimitaPorIp() {
        assertDoesNotThrow(() -> servicio.verificarRegistro("1.2.3.4"));
        verify(limitador).registrar("limite:registro:ip:1.2.3.4", 7, Duration.ofHours(1));

        when(limitador.registrar(anyString(), anyInt(), any())).thenReturn(EXCEDIDO);
        assertThrows(NegocioException.class, () -> servicio.verificarRegistro("1.2.3.4"));
    }

    @Test
    void laDisponibilidadSeLimitaPorIp() {
        assertDoesNotThrow(() -> servicio.verificarDisponibilidad("1.2.3.4"));
        verify(limitador).registrar("limite:disponibilidad:ip:1.2.3.4", 30, Duration.ofMinutes(15));

        when(limitador.registrar(anyString(), anyInt(), any())).thenReturn(EXCEDIDO);
        assertThrows(NegocioException.class, () -> servicio.verificarDisponibilidad("1.2.3.4"));
    }
}
