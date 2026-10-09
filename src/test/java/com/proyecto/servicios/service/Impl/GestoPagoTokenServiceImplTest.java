package com.proyecto.servicios.service.Impl;

import com.proyecto.servicios.client.GestoPagoAuthClient;
import com.proyecto.servicios.client.GestoPagoAuthException;
import com.proyecto.servicios.entity.gestopago.GestoPagoToken;
import com.proyecto.servicios.mapper.GestoPagoTokenMapper;
import com.proyecto.servicios.model.gestopago.GestoPagoAuthResponse;
import com.proyecto.servicios.repositorys.gestopago.GestoPagoTokenRepository;
import feign.FeignException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GestoPagoTokenServiceImplTest {

    private static final Integer DISTRIBUTOR_ID = 42;
    private static final String DEVICE_CODE = "device-test";
    private static final String PASSWORD = "password-test";
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-08T12:00:00Z"), ZoneOffset.UTC);

    @Mock
    private GestoPagoAuthClient authClient;
    @Mock
    private GestoPagoTokenRepository repository;
    @Mock
    private GestoPagoTokenMapper mapper;

    private GestoPagoTokenServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new GestoPagoTokenServiceImpl(
                authClient, repository, mapper, DISTRIBUTOR_ID, DEVICE_CODE, PASSWORD, 3_600_000, CLOCK);
    }

    @Test
    void obtenerTokenActivo_reutilizaTokenFrescoSinConsultarAutenticacion() {
        GestoPagoToken fresh = token("still-fresh", LocalDateTime.now(CLOCK).minusMinutes(5), 3_600L);
        when(repository.findByIdDistribuidorAndCodigoDispositivoAndActivoTrue(DISTRIBUTOR_ID, DEVICE_CODE))
                .thenReturn(Optional.of(fresh));

        GestoPagoToken result = service.obtenerTokenActivo(DISTRIBUTOR_ID, DEVICE_CODE).orElseThrow();

        assertSame(fresh, result);
        verifyNoInteractions(authClient, mapper);
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void obtenerTokenActivo_siNoExisteTokenRenuevaYPersisteUnoNuevo() {
        when(repository.findByIdDistribuidorAndCodigoDispositivoAndActivoTrue(DISTRIBUTOR_ID, DEVICE_CODE))
                .thenReturn(Optional.empty());
        GestoPagoAuthResponse response = authResponse("new-token", 3_600L);
        GestoPagoToken newToken = token("new-token", LocalDateTime.now(CLOCK), 3_600L);
        when(repository.findByIdDistribuidorAndCodigoDispositivo(DISTRIBUTOR_ID, DEVICE_CODE))
                .thenReturn(Optional.empty());
        when(authClient.authenticate(DISTRIBUTOR_ID, DEVICE_CODE, PASSWORD)).thenReturn(response);
        when(mapper.toEntity(response)).thenReturn(newToken);
        when(repository.saveAndFlush(any(GestoPagoToken.class))).thenAnswer(invocation -> invocation.getArgument(0));

        GestoPagoToken result = service.obtenerTokenActivo(DISTRIBUTOR_ID, DEVICE_CODE).orElseThrow();

        assertSame(newToken, result);
        assertEquals("new-token", result.getToken());
        assertEquals(DISTRIBUTOR_ID, result.getIdDistribuidor());
        assertEquals(DEVICE_CODE, result.getCodigoDispositivo());
        assertEquals(Boolean.TRUE, result.getActivo());
        verify(authClient).authenticate(DISTRIBUTOR_ID, DEVICE_CODE, PASSWORD);
        verify(repository).saveAndFlush(newToken);
    }

    @Test
    void obtenerTokenActivo_siTokenExpiradoRenuevaElRegistroExistente() {
        usarMapperReal();
        GestoPagoToken expired = token("expired-token", LocalDateTime.now(CLOCK).minusMinutes(10), 60L);
        expired.setId(5);
        when(repository.findByIdDistribuidorAndCodigoDispositivoAndActivoTrue(DISTRIBUTOR_ID, DEVICE_CODE))
            .thenReturn(Optional.of(expired));
        when(repository.findByIdDistribuidorAndCodigoDispositivo(DISTRIBUTOR_ID, DEVICE_CODE))
            .thenReturn(Optional.of(expired));
        when(authClient.authenticate(DISTRIBUTOR_ID, DEVICE_CODE, PASSWORD))
            .thenReturn(authResponse("new-token", 3_600L));
        when(repository.saveAndFlush(any(GestoPagoToken.class))).thenAnswer(invocation -> invocation.getArgument(0));

        GestoPagoToken result = service.obtenerTokenActivo(DISTRIBUTOR_ID, DEVICE_CODE).orElseThrow();

        assertSame(expired, result);
        assertEquals(5, result.getId());
        assertEquals("new-token", result.getToken());
        assertEquals(3_600L, result.getExpiresIn());
        assertEquals(DISTRIBUTOR_ID, result.getIdDistribuidor());
        assertEquals(DEVICE_CODE, result.getCodigoDispositivo());
        verify(repository).saveAndFlush(expired);
    }
    @Test
    void renovarToken_siProveedorDevuelveRespuestaSinTokenClasificaRespuestaInvalida() {
        when(authClient.authenticate(DISTRIBUTOR_ID, DEVICE_CODE, PASSWORD)).thenReturn(authResponse(" ", 3_600L));

        GestoPagoAuthException exception = assertThrows(GestoPagoAuthException.class, service::renovarToken);

        assertEquals(GestoPagoAuthException.Tipo.RESPUESTA_INVALIDA, exception.getTipo());
        assertNull(exception.getStatusHttp());
        verify(repository, never()).saveAndFlush(any());
        verifyNoInteractions(mapper);
    }

    @Test
    void renovarToken_siProveedorRechazaCredencialesClasificaAutenticacion() {
        FeignException unauthorized = feignFailure(401, "Unauthorized");
        when(authClient.authenticate(DISTRIBUTOR_ID, DEVICE_CODE, PASSWORD)).thenThrow(unauthorized);

        GestoPagoAuthException exception = assertThrows(GestoPagoAuthException.class, service::renovarToken);

        assertEquals(GestoPagoAuthException.Tipo.AUTENTICACION, exception.getTipo());
        assertEquals(401, exception.getStatusHttp());
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void renovarToken_siProveedorAgotaElTiempoClasificaTimeout() {
        FeignException timeout = feignFailure(-1, "Read timed out");
        when(authClient.authenticate(DISTRIBUTOR_ID, DEVICE_CODE, PASSWORD)).thenThrow(timeout);

        GestoPagoAuthException exception = assertThrows(GestoPagoAuthException.class, service::renovarToken);

        assertEquals(GestoPagoAuthException.Tipo.TIMEOUT, exception.getTipo());
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void obtenerTokenActivo_siRenovacionAnticipadaFallaUsaTokenAunVigente() {
        GestoPagoToken nearExpiry = token("still-valid", LocalDateTime.now(CLOCK).minusSeconds(57), 60L);
        when(repository.findByIdDistribuidorAndCodigoDispositivoAndActivoTrue(DISTRIBUTOR_ID, DEVICE_CODE))
                .thenReturn(Optional.of(nearExpiry));
        FeignException unauthorized = feignFailure(401, "Unauthorized");
        when(authClient.authenticate(DISTRIBUTOR_ID, DEVICE_CODE, PASSWORD)).thenThrow(unauthorized);

        GestoPagoToken result = service.obtenerTokenActivo(DISTRIBUTOR_ID, DEVICE_CODE).orElseThrow();

        assertSame(nearExpiry, result);
        verify(authClient).authenticate(DISTRIBUTOR_ID, DEVICE_CODE, PASSWORD);
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void obtenerTokenActivo_noDevuelveUnTokenQueYaVencioSiFallaLaRenovacion() {
        GestoPagoToken expired = token("expired", LocalDateTime.now(CLOCK).minusSeconds(60), 60L);
        when(repository.findByIdDistribuidorAndCodigoDispositivoAndActivoTrue(DISTRIBUTOR_ID, DEVICE_CODE))
            .thenReturn(Optional.of(expired));
        FeignException unauthorized = feignFailure(401, "Unauthorized");
        when(authClient.authenticate(DISTRIBUTOR_ID, DEVICE_CODE, PASSWORD))
            .thenThrow(unauthorized);

        var exception = assertThrows(GestoPagoAuthException.class,
            () -> service.obtenerTokenActivo(DISTRIBUTOR_ID, DEVICE_CODE));

        assertEquals(GestoPagoAuthException.Tipo.AUTENTICACION, exception.getTipo());
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void revisarRenovacionProgramada_omiteTokenFresco() {
        GestoPagoToken fresh = token("fresh", LocalDateTime.now(CLOCK).minusMinutes(5), 3_600L);
        when(repository.findByIdDistribuidorAndCodigoDispositivoAndActivoTrue(DISTRIBUTOR_ID, DEVICE_CODE))
                .thenReturn(Optional.of(fresh));

        service.revisarRenovacionProgramada();

        verifyNoInteractions(authClient, mapper);
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void revisarRenovacionProgramada_renuevaElRegistroVencidoSinCrearOtro() {
        usarMapperReal();
        GestoPagoToken expired = token("expired", LocalDateTime.now(CLOCK).minusMinutes(10), 60L);
        expired.setId(5);
        when(repository.findByIdDistribuidorAndCodigoDispositivoAndActivoTrue(DISTRIBUTOR_ID, DEVICE_CODE))
            .thenReturn(Optional.of(expired));
        when(repository.findByIdDistribuidorAndCodigoDispositivo(DISTRIBUTOR_ID, DEVICE_CODE))
            .thenReturn(Optional.of(expired));
        when(authClient.authenticate(DISTRIBUTOR_ID, DEVICE_CODE, PASSWORD))
            .thenReturn(authResponse("renewed", 3_600L));
        when(repository.saveAndFlush(any(GestoPagoToken.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.revisarRenovacionProgramada();

        verify(repository).saveAndFlush(expired);
        assertEquals(5, expired.getId());
        assertEquals("renewed", expired.getToken());
        assertEquals(3_600L, expired.getExpiresIn());
    }

    private void usarMapperReal() {
        service = new GestoPagoTokenServiceImpl(authClient, repository,
            org.mapstruct.factory.Mappers.getMapper(GestoPagoTokenMapper.class),
            DISTRIBUTOR_ID, DEVICE_CODE, PASSWORD, 3_600_000, CLOCK);
    }
    private static GestoPagoAuthResponse authResponse(String token, long expiresIn) {
        GestoPagoAuthResponse response = new GestoPagoAuthResponse();
        response.setToken(token);
        response.setExpiresIn(expiresIn);
        return response;
    }

    private static GestoPagoToken token(String token, LocalDateTime issuedAt, long expiresIn) {
        GestoPagoToken entity = new GestoPagoToken();
        entity.setToken(token);
        entity.setIdDistribuidor(DISTRIBUTOR_ID);
        entity.setCodigoDispositivo(DEVICE_CODE);
        entity.setExpiresIn(expiresIn);
        entity.setFechaCreacion(issuedAt);
        entity.setFechaActualizacion(issuedAt);
        entity.setActivo(true);
        return entity;
    }

    private static FeignException feignFailure(int status, String message) {
        FeignException exception = mock(FeignException.class);
        when(exception.status()).thenReturn(status);
        if (status < 0) {
            when(exception.getMessage()).thenReturn(message);
        }
        return exception;
    }
}
