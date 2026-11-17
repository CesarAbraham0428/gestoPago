package com.proyecto.servicios.service.Impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.proyecto.servicios.client.GestoPagoAuthClient;
import com.proyecto.servicios.client.GestoPagoAuthException;
import com.proyecto.servicios.entity.gestopago.GestoPagoToken;
import com.proyecto.servicios.mapper.GestoPagoTokenMapper;
import com.proyecto.servicios.models.response.gestopago.GestoPagoAuthResponse;
import com.proyecto.servicios.repositorys.gestopago.GestoPagoTokenRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

class GestoPagoTokenServiceImplComplementoTest {

    private static final Clock RELOJ = Clock.fixed(Instant.parse("2026-11-01T12:00:00Z"), ZoneOffset.UTC);
    private final GestoPagoAuthClient auth = mock(GestoPagoAuthClient.class);
    private final GestoPagoTokenRepository repositorio = mock(GestoPagoTokenRepository.class);
    private final GestoPagoTokenMapper mapper = Mappers.getMapper(GestoPagoTokenMapper.class);
    private final GestoPagoTokenServiceImpl servicio =
            new GestoPagoTokenServiceImpl(auth, repositorio, mapper, 42, "device", "clave", 3_600_000L, RELOJ);

    private static GestoPagoAuthResponse respuesta(String token, Long expiraEn) {
        GestoPagoAuthResponse respuesta = new GestoPagoAuthResponse();
        respuesta.setToken(token);
        respuesta.setExpiresIn(expiraEn);
        return respuesta;
    }

    @Test
    void elConstructorPublicoUsaElRelojDelSistema() {
        GestoPagoTokenServiceImpl publico =
                new GestoPagoTokenServiceImpl(auth, repositorio, mapper, 42, "device", "clave", 3_600_000L);
        GestoPagoToken fresco = new GestoPagoToken();
        fresco.setToken("t");
        fresco.setExpiresIn(3600L);
        fresco.setFechaActualizacion(LocalDateTime.now());
        when(repositorio.findByIdDistribuidorAndCodigoDispositivoAndActivoTrue(42, "device"))
                .thenReturn(Optional.of(fresco));

        publico.renovarSiHaceFalta();

        verify(auth, never()).authenticate(any(), any(), any());
    }

    @Test
    void sinTokenActivoRenovarSiHaceFaltaPideUnoNuevo() {
        when(repositorio.findByIdDistribuidorAndCodigoDispositivoAndActivoTrue(42, "device"))
                .thenReturn(Optional.empty());
        when(repositorio.findByIdDistribuidorAndCodigoDispositivo(42, "device")).thenReturn(Optional.empty());
        when(auth.authenticate(42, "device", "clave")).thenReturn(respuesta("nuevo", 3600L));
        when(repositorio.saveAndFlush(any(GestoPagoToken.class))).thenAnswer(inv -> inv.getArgument(0));

        servicio.renovarSiHaceFalta();

        verify(repositorio).saveAndFlush(any(GestoPagoToken.class));
    }

    @Test
    void renovarTokenGuardaElTokenNuevoComoActivoConLosDatosDelDistribuidor() {
        when(repositorio.findByIdDistribuidorAndCodigoDispositivo(42, "device")).thenReturn(Optional.empty());
        when(auth.authenticate(42, "device", "clave")).thenReturn(respuesta("nuevo", null));
        when(repositorio.saveAndFlush(any(GestoPagoToken.class))).thenAnswer(inv -> inv.getArgument(0));

        GestoPagoToken guardado = servicio.renovarToken();

        assertEquals("nuevo", guardado.getToken());
        assertEquals(42, guardado.getIdDistribuidor());
        assertEquals("device", guardado.getCodigoDispositivo());
    }

    @Test
    void unaRespuestaNulaSinTokenOConVigenciaNoPositivaEsInvalida() {
        when(auth.authenticate(42, "device", "clave")).thenReturn(null, respuesta(null, 10L), respuesta(" ", 10L),
                respuesta("t", 0L), respuesta("t", -1L));

        for (int i = 0; i < 5; i++) {
            GestoPagoAuthException error = assertThrows(GestoPagoAuthException.class, servicio::renovarToken);
            assertEquals(GestoPagoAuthException.Tipo.RESPUESTA_INVALIDA, error.getTipo(), "caso " + i);
        }
        verify(repositorio, never()).saveAndFlush(any());
    }

    @Test
    void sinTokenPrevioUnaRenovacionFallidaSePropaga() {
        when(repositorio.findByIdDistribuidorAndCodigoDispositivoAndActivoTrue(42, "device"))
                .thenReturn(Optional.empty());
        when(auth.authenticate(42, "device", "clave")).thenReturn(null);

        assertThrows(GestoPagoAuthException.class, () -> servicio.obtenerTokenActivo(42, "device"));
    }
}
