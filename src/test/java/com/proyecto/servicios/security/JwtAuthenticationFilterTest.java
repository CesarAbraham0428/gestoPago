package com.proyecto.servicios.security;

import com.auth0.jwt.interfaces.DecodedJWT;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.proyecto.servicios.repositorys.sf.UsuarioRepository;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class JwtAuthenticationFilterTest {
    @AfterEach void limpiar() { SecurityContextHolder.clearContext(); }

    @Test void verificaEstadoSinCargarUsuarioYCliente() throws Exception { comprobar(true); }
    @Test void rechazaUsuarioOClienteInactivo() throws Exception { comprobar(false); }

    private void comprobar(boolean activo) throws Exception {
        JwtTokenService tokens = mock(JwtTokenService.class);
        UsuarioRepository usuarios = mock(UsuarioRepository.class);
        DecodedJWT jwt = mock(DecodedJWT.class);
        when(tokens.validar("token")).thenReturn(jwt);
        when(jwt.getSubject()).thenReturn("1");
        when(usuarios.existsByIdAndActivoTrueAndClienteActivoTrue(1)).thenReturn(activo);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/clientes");
        request.addHeader("Authorization", "Bearer token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);
        new JwtAuthenticationFilter(tokens, usuarios).doFilter(request, response, chain);
        verify(usuarios, never()).findById(1);
        if (activo) {
            verify(chain).doFilter(request, response);
            assertNotNull(SecurityContextHolder.getContext().getAuthentication());
            assertEquals("1", SecurityContextHolder.getContext().getAuthentication().getName());
            assertTrue(SecurityContextHolder.getContext().getAuthentication().isAuthenticated());
        } else {
            assertEquals(401, response.getStatus());
            verifyNoInteractions(chain);
            assertNull(SecurityContextHolder.getContext().getAuthentication());
        }
    }

    @Test void tokenConFirmaInvalidaNoConsultaUsuariosNiContinuaLaPeticion() throws Exception {
        JwtTokenService tokens = mock(JwtTokenService.class);
        UsuarioRepository usuarios = mock(UsuarioRepository.class);
        when(tokens.validar("invalido")).thenThrow(new JWTVerificationException("Firma inválida"));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/clientes");
        request.addHeader("Authorization", "Bearer invalido");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        new JwtAuthenticationFilter(tokens, usuarios).doFilter(request, response, chain);

        assertEquals(401, response.getStatus());
        assertTrue(response.getContentAsString().contains("Token inválido o vencido"));
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verifyNoInteractions(usuarios, chain);
    }

    @Test void loginPublicoOmiteLaVerificacionDeUnBearerAnterior() throws Exception {
        JwtTokenService tokens = mock(JwtTokenService.class);
        UsuarioRepository usuarios = mock(UsuarioRepository.class);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/auth/login");
        request.setServletPath("/auth/login");
        request.addHeader("Authorization", "Bearer vencido");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        new JwtAuthenticationFilter(tokens, usuarios).doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        verifyNoInteractions(tokens, usuarios);
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }
}
