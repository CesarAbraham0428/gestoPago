package com.proyecto.servicios.security;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.proyecto.servicios.service.SesionService;
import com.proyecto.servicios.service.SesionService.Estado;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

class JwtAuthenticationFilterTest {
    private final JwtTokenService tokens = mock(JwtTokenService.class);
    private final SesionService sesiones = mock(SesionService.class);
    private final FilterChain chain = mock(FilterChain.class);
    private final MockHttpServletResponse response = new MockHttpServletResponse();
    private final JwtAuthenticationFilter filtro =
            new JwtAuthenticationFilter(tokens, sesiones, new ApiSecurityErrorWriter(new ObjectMapper()));

    @AfterEach
    void limpiar() {
        SecurityContextHolder.clearContext();
    }

    private MockHttpServletRequest conBearer(String metodo, String ruta, String token) {
        MockHttpServletRequest request = new MockHttpServletRequest(metodo, ruta);
        request.setServletPath(ruta);
        request.addHeader("Authorization", "Bearer " + token);
        return request;
    }

    private void tokenDe(String usuario, String jti) {
        DecodedJWT jwt = mock(DecodedJWT.class);
        when(tokens.validar("token")).thenReturn(jwt);
        when(jwt.getSubject()).thenReturn(usuario);
        when(jwt.getId()).thenReturn(jti);
    }

    @Test
    void sesionVigenteAutenticaYContinua() throws Exception {
        tokenDe("1", "jti-1");
        when(sesiones.estado(1, "jti-1")).thenReturn(Estado.VALIDA);
        var request = conBearer("GET", "/clientes", "token");

        filtro.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        var auth = SecurityContextHolder.getContext().getAuthentication();
        assertNotNull(auth);
        assertEquals("1", auth.getName());
        assertEquals("jti-1", auth.getDetails());
        assertTrue(auth.isAuthenticated());
    }

    @ParameterizedTest
    @CsvSource({
            "USUARIO_INACTIVO, Usuario inactivo o inexistente",
            "REEMPLAZADA, otro dispositivo",
            "FINALIZADA, ya no está activa"})
    void sesionNoVigenteResponde401ConMensajePropio(Estado estado, String fragmento) throws Exception {
        tokenDe("1", "jti-1");
        when(sesiones.estado(1, "jti-1")).thenReturn(estado);

        filtro.doFilter(conBearer("GET", "/clientes", "token"), response, chain);

        assertEquals(401, response.getStatus());
        assertTrue(response.getContentAsString().contains(fragmento));
        verifyNoInteractions(chain);
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void tokenConFirmaInvalidaNoConsultaSesionesNiContinua() throws Exception {
        when(tokens.validar("invalido")).thenThrow(new JWTVerificationException("Firma inválida"));

        filtro.doFilter(conBearer("GET", "/clientes", "invalido"), response, chain);

        assertEquals(401, response.getStatus());
        assertTrue(response.getContentAsString().contains("Tu sesión expiró o no es válida"));
        var body = new ObjectMapper().readTree(response.getContentAsString());
        assertEquals(2, body.size());
        assertEquals(1, body.get("codigo").asInt());
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verifyNoInteractions(sesiones, chain);
    }

    @Test
    void errorDeBaseDeDatosFallaCerradoConFormatoUniformeSinSql() throws Exception {
        tokenDe("1", "jti-1");
        when(sesiones.estado(1, "jti-1")).thenThrow(
                new org.springframework.dao.DataAccessResourceFailureException("SELECT password_hash FROM usuarios"));

        filtro.doFilter(conBearer("GET", "/usuarios/me", "token"), response, chain);

        assertEquals(500, response.getStatus());
        var body = new ObjectMapper().readTree(response.getContentAsString());
        assertEquals(1, body.get("codigo").asInt());
        assertEquals(2, body.size());
        assertFalse(response.getContentAsString().contains("SELECT"));
        verifyNoInteractions(chain);
    }

    @ParameterizedTest
    @CsvSource({"/auth/login", "/auth/verificar", "/clientes"})
    void rutasPublicasOmitenLaVerificacionDeUnBearerAnterior(String ruta) throws Exception {
        var request = conBearer("POST", ruta, "vencido");

        filtro.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        verifyNoInteractions(tokens, sesiones);
    }

    @Test
    void logoutNoEsPublicoYExigeSesionVigente() throws Exception {
        tokenDe("1", "jti-1");
        when(sesiones.estado(1, "jti-1")).thenReturn(Estado.FINALIZADA);

        filtro.doFilter(conBearer("POST", "/auth/logout", "token"), response, chain);

        assertEquals(401, response.getStatus());
        verifyNoInteractions(chain);
    }
}
