package com.proyecto.servicios.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.proyecto.servicios.service.exception.AutenticacionException;
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
    private final LimiteAutenticacionService servicio = new LimiteAutenticacionService(limitador, 5, 5, 7, 30);

    @BeforeEach
    void sinExceso() {
        when(limitador.registrar(anyString(), anyInt(), any())).thenReturn(Resultado.PERMITIDO);
        when(limitador.consultar(anyString(), anyInt(), any())).thenReturn(Resultado.PERMITIDO);
    }

    @Test
    void elInicioDeSesionSoloConsultaPorIpYPorCorreoNormalizadoSinContar() {
        servicio.verificarInicioDeSesion("1.2.3.4", "  Ana@Correo.MX ");

        verify(limitador).consultar("limite:login:ip:1.2.3.4", 5, Duration.ofMinutes(1));
        verify(limitador).consultar("limite:login:correo:ana@correo.mx", 5, Duration.ofMinutes(1));
        verify(limitador, never()).registrar(anyString(), anyInt(), any());
    }

    @Test
    void sinCorreoSoloSeConsultaLaIp() {
        assertDoesNotThrow(() -> servicio.verificarInicioDeSesion("1.2.3.4", null));

        verify(limitador).consultar(anyString(), eq(5), any(Duration.class));
    }

    @Test
    void conLosFallosAgotadosPorIpRespondeTooManyRequests() {
        when(limitador.consultar(eq("limite:login:ip:1.2.3.4"), anyInt(), any())).thenReturn(EXCEDIDO);

        NegocioException error = assertThrows(NegocioException.class,
                () -> servicio.verificarInicioDeSesion("1.2.3.4", "a@b.mx"));

        assertEquals(HttpStatus.TOO_MANY_REQUESTS, error.getStatus());
        assertEquals(120L, error.getReintentarEnSegundos());
    }

    @Test
    void conLosFallosAgotadosPorCorreoTambienBloquea() {
        when(limitador.consultar(eq("limite:login:correo:a@b.mx"), anyInt(), any())).thenReturn(EXCEDIDO);

        assertThrows(NegocioException.class, () -> servicio.verificarInicioDeSesion("1.2.3.4", "a@b.mx"));
    }

    @Test
    void elRechazoInformaElMayorTiempoDeEsperaParaReintentar() {
        when(limitador.consultar(eq("limite:login:ip:1.2.3.4"), anyInt(), any())).thenReturn(new Resultado(true, 30));
        when(limitador.consultar(eq("limite:login:correo:a@b.mx"), anyInt(), any()))
                .thenReturn(new Resultado(true, 600));

        NegocioException error = assertThrows(NegocioException.class,
                () -> servicio.verificarInicioDeSesion("1.2.3.4", "a@b.mx"));

        assertEquals(600L, error.getReintentarEnSegundos());
    }

    @Test
    void lasCredencialesIncorrectasSumanUnFalloPorIpYPorCorreo() {
        servicio.registrarFalloDeLogin("1.2.3.4", " A@B.mx ",
                new AutenticacionException(AutenticacionException.Tipo.CREDENCIALES_INVALIDAS));

        verify(limitador).registrar("limite:login:ip:1.2.3.4", 5, Duration.ofMinutes(1));
        verify(limitador).registrar("limite:login:correo:a@b.mx", 5, Duration.ofMinutes(1));
    }

    @Test
    void unCodigoIncorrectoSumaUnFalloSoloPorIp() {
        servicio.registrarFalloDeLogin("1.2.3.4", null,
                new NegocioException(HttpStatus.UNAUTHORIZED, "Código de verificación incorrecto"));

        verify(limitador).registrar("limite:login:ip:1.2.3.4", 5, Duration.ofMinutes(1));
        verifyNoMoreInteractions(limitador);
    }

    @Test
    void losErroresQueNoSonCredencialesIncorrectasNoSuman() {
        servicio.registrarFalloDeLogin("1.2.3.4", "a@b.mx",
                new AutenticacionException(AutenticacionException.Tipo.CUENTA_INACTIVA));
        servicio.registrarFalloDeLogin("1.2.3.4", "a@b.mx", new NegocioException(HttpStatus.CONFLICT, "Sesión"));

        verifyNoInteractions(limitador);
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
