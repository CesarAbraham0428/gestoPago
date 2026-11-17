package com.proyecto.servicios.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.proyecto.servicios.repositorys.sf.SesionUsuarioRepository;
import com.proyecto.servicios.repositorys.sf.UsuarioRepository;
import com.proyecto.servicios.security.JwtTokenService.TokenEmitido;
import com.proyecto.servicios.service.SesionService.Estado;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class SesionServiceUnitTest {

    private static final Instant AHORA = Instant.parse("2026-11-01T10:00:00Z");
    private final SesionUsuarioRepository sesiones = mock(SesionUsuarioRepository.class);
    private final UsuarioRepository usuarios = mock(UsuarioRepository.class);
    private final SesionService servicio =
            new SesionService(sesiones, usuarios, Clock.fixed(AHORA, ZoneOffset.UTC));

    @Test
    void elTokenDeLaSesionVigenteEsValido() {
        when(sesiones.jtiVigente(1, AHORA)).thenReturn(Optional.of("jti"));

        assertEquals(Estado.VALIDA, servicio.estado(1, "jti"));
    }

    @Test
    void unUsuarioInactivoSeRechazaAunqueTengaSesion() {
        when(sesiones.jtiVigente(1, AHORA)).thenReturn(Optional.of("otro"));
        when(usuarios.existsByIdAndActivoTrueAndClienteActivoTrue(1)).thenReturn(false);

        assertEquals(Estado.USUARIO_INACTIVO, servicio.estado(1, "jti"));
    }

    @Test
    void otraSesionVigenteReemplazaALaAnterior() {
        when(sesiones.jtiVigente(1, AHORA)).thenReturn(Optional.of("otro"));
        when(usuarios.existsByIdAndActivoTrueAndClienteActivoTrue(1)).thenReturn(true);

        assertEquals(Estado.REEMPLAZADA, servicio.estado(1, "jti"));
    }

    @Test
    void sinSesionVigenteLaSesionFinalizo() {
        when(sesiones.jtiVigente(1, AHORA)).thenReturn(Optional.empty());
        when(usuarios.existsByIdAndActivoTrueAndClienteActivoTrue(1)).thenReturn(true);

        assertEquals(Estado.FINALIZADA, servicio.estado(1, "jti"));
    }

    @Test
    void abrirDevuelveSiSeCreoLaSesion() {
        Instant expira = AHORA.plusSeconds(60);
        when(sesiones.abrir(1, "j", AHORA, expira, false)).thenReturn(1);
        when(sesiones.abrir(2, "j", AHORA, expira, false)).thenReturn(0);

        assertTrue(servicio.abrir(1, new TokenEmitido("t", "j", expira), false));
        assertFalse(servicio.abrir(2, new TokenEmitido("t", "j", expira), false));
    }

    @Test
    void cerrarEliminaLaSesion() {
        servicio.cerrar(5);

        verify(sesiones).cerrar(5);
    }

    @Test
    void elConstructorPublicoUsaElRelojDelSistema() {
        when(sesiones.jtiVigente(org.mockito.ArgumentMatchers.eq(1), org.mockito.ArgumentMatchers.any()))
                .thenReturn(Optional.of("j"));

        assertEquals(Estado.VALIDA, new SesionService(sesiones, usuarios).estado(1, "j"));
    }
}
