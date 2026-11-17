package com.proyecto.servicios.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.proyecto.servicios.repositorys.sf.SesionUsuarioRepository;
import com.proyecto.servicios.repositorys.sf.UsuarioRepository;
import com.proyecto.servicios.security.JwtTokenService.TokenEmitido;
import com.proyecto.servicios.service.SesionService.Estado;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class SesionServiceTest {

    private static final Instant AHORA = Instant.parse("2026-01-01T12:00:00Z");

    private final SesionUsuarioRepository sesiones = mock(SesionUsuarioRepository.class);
    private final UsuarioRepository usuarios = mock(UsuarioRepository.class);
    private final SesionService service =
            new SesionService(sesiones, usuarios, Clock.fixed(AHORA, ZoneOffset.UTC));

    @Test
    void elTokenDeLaSesionVigenteEsValidoConUnaSolaConsulta() {
        when(sesiones.jtiVigente(7, AHORA)).thenReturn(Optional.of("jti-1"));
        assertEquals(Estado.VALIDA, service.estado(7, "jti-1"));
        verifyNoInteractions(usuarios);
    }

    @Test
    void unTokenAnteriorQuedaReemplazado() {
        when(sesiones.jtiVigente(7, AHORA)).thenReturn(Optional.of("jti-nuevo"));
        when(usuarios.existsByIdAndActivoTrueAndClienteActivoTrue(7)).thenReturn(true);
        assertEquals(Estado.REEMPLAZADA, service.estado(7, "jti-viejo"));
    }

    @Test
    void sinSesionRegistradaElTokenEstaFinalizado() {
        when(sesiones.jtiVigente(7, AHORA)).thenReturn(Optional.empty());
        when(usuarios.existsByIdAndActivoTrueAndClienteActivoTrue(7)).thenReturn(true);
        assertEquals(Estado.FINALIZADA, service.estado(7, "jti-1"));
    }

    @Test
    void usuarioDadoDeBajaNoTieneSesionValida() {
        when(sesiones.jtiVigente(7, AHORA)).thenReturn(Optional.empty());
        when(usuarios.existsByIdAndActivoTrueAndClienteActivoTrue(7)).thenReturn(false);
        assertEquals(Estado.USUARIO_INACTIVO, service.estado(7, "jti-1"));
    }

    @Test
    void abrirDelegaEnLaOperacionAtomicaYReportaElConflicto() {
        TokenEmitido token = new TokenEmitido("jwt", "jti-1", AHORA.plusSeconds(60));
        when(sesiones.abrir(7, "jti-1", AHORA, token.expiraEn(), false)).thenReturn(0);
        when(sesiones.abrir(7, "jti-1", AHORA, token.expiraEn(), true)).thenReturn(1);

        assertFalse(service.abrir(7, token, false));
        assertTrue(service.abrir(7, token, true));
    }
}
