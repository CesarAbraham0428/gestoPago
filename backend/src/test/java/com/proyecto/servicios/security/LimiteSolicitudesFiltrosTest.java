package com.proyecto.servicios.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.proyecto.servicios.utils.LimitadorVentanaFija;
import com.proyecto.servicios.utils.LimitadorVentanaFija.Resultado;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

/** Ramas de los filtros de límite que la cadena completa de Spring no alcanza (CORS responde antes, etc.). */
class LimiteSolicitudesFiltrosTest {

    private static final Duration VENTANA = Duration.ofMinutes(1);

    private final LimitadorVentanaFija limitador = mock(LimitadorVentanaFija.class);
    private final ApiSecurityErrorWriter errores = mock(ApiSecurityErrorWriter.class);
    private final LimiteSolicitudesPorIpFilter porIp = new LimiteSolicitudesPorIpFilter(limitador, errores, 100, 5);
    private final LimiteSolicitudesPorUsuarioFilter porUsuario =
            new LimiteSolicitudesPorUsuarioFilter(limitador, errores, 100);

    @AfterEach
    void limpiarContexto() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void lasPreflightYLaSondaDeSaludNoSeCuentan() {
        assertTrue(porIp.shouldNotFilter(new MockHttpServletRequest("OPTIONS", "/clientes")));
        assertTrue(porIp.shouldNotFilter(new MockHttpServletRequest("GET", "/actuator/health")));
        assertFalse(porIp.shouldNotFilter(new MockHttpServletRequest("GET", "/clientes")));
    }

    @Test
    void enUnaRutaPublicaExcederElLimiteGeneralTambienBloquea() {
        when(limitador.registrar(startsWith("limite:global:ip:"), anyInt(), any())).thenReturn(new Resultado(true, 40));
        when(limitador.registrar(startsWith("limite:rafaga:ip:"), anyInt(), any())).thenReturn(Resultado.PERMITIDO);

        Resultado resultado = porIp.contar(new MockHttpServletRequest("POST", "/auth/login"), limitador, VENTANA);

        assertTrue(resultado.excedido());
        assertEquals(40, resultado.reintentarEnSegundos());
    }

    @Test
    void sinAutenticacionONoAutenticadoNoSeCuentaPorUsuario() {
        MockHttpServletRequest peticion = new MockHttpServletRequest("GET", "/clientes");

        assertSame(Resultado.PERMITIDO, porUsuario.contar(peticion, limitador, VENTANA));
        SecurityContextHolder.getContext().setAuthentication(new AnonymousAuthenticationToken("clave", "anonimo",
                List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS"))));
        assertSame(Resultado.PERMITIDO, porUsuario.contar(peticion, limitador, VENTANA));
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("ana", "x"));
        assertSame(Resultado.PERMITIDO, porUsuario.contar(peticion, limitador, VENTANA));

        verifyNoInteractions(limitador);
    }

    @Test
    void elUsuarioAutenticadoSeCuentaYSeIdentificaEnElRegistro() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("ana", "x", List.of()));
        Resultado excedido = new Resultado(true, 10);
        when(limitador.registrar(eq("limite:global:usuario:ana"), eq(100), eq(VENTANA))).thenReturn(excedido);
        MockHttpServletRequest peticion = new MockHttpServletRequest("GET", "/clientes");

        assertSame(excedido, porUsuario.contar(peticion, limitador, VENTANA));
        assertEquals("usuario ana", porUsuario.origen(peticion));
    }
}
