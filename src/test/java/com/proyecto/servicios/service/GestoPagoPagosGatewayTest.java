package com.proyecto.servicios.service;

import com.proyecto.servicios.client.GestoPagoAuthException;
import com.proyecto.servicios.client.GestoPagoPagoException;
import com.proyecto.servicios.client.GestoPagoPagosClient;
import com.proyecto.servicios.entity.gestopago.GestoPagoToken;
import com.proyecto.servicios.models.response.gestopago.GestoPagoPagosResponse;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class GestoPagoPagosGatewayTest {

    private final GestoPagoPagosClient pagosClient = mock(GestoPagoPagosClient.class);
    private final GestoPagoTokenService tokenService = mock(GestoPagoTokenService.class);
    private final GestoPagoPagosGateway gateway = new GestoPagoPagosGateway(pagosClient, tokenService, 10, "DISP");
    private final GestoPagoPagosResponse respuesta = new GestoPagoPagosResponse();

    @BeforeEach
    void preparar() {
        when(tokenService.obtenerTokenActivo(10, "DISP")).thenReturn(Optional.of(token("tok-1")));
    }

    @Test
    void cadaOperacionUsaElTokenActivoYLaPeticionFirmada() {
        when(pagosClient.enviar("tok-1", "firmada-1")).thenReturn(respuesta);
        when(pagosClient.confirmar("tok-1", "firmada-2")).thenReturn(respuesta);
        when(pagosClient.verificarReferencia("tok-1", "firmada-3")).thenReturn(respuesta);

        assertSame(respuesta, gateway.enviar("firmada-1"));
        assertSame(respuesta, gateway.confirmar("firmada-2"));
        assertSame(respuesta, gateway.verificarReferencia("firmada-3"));

        verify(tokenService, never()).renovarToken();
    }

    @Test
    void tokenVencidoSeRenuevaYSeReintentaUnaVez() {
        when(tokenService.renovarToken()).thenReturn(token("tok-2"));
        when(pagosClient.enviar(eq("tok-1"), any())).thenThrow(autenticacion());
        when(pagosClient.enviar(eq("tok-2"), any())).thenReturn(respuesta);

        assertSame(respuesta, gateway.enviar("firmada"));

        verify(tokenService).renovarToken();
        verify(pagosClient, times(2)).enviar(any(), eq("firmada"));
    }

    @Test
    void siElTokenRenovadoTambienSeRechazaNoSeReintentaOtraVez() {
        when(tokenService.renovarToken()).thenReturn(token("tok-2"));
        when(pagosClient.enviar(any(), any())).thenThrow(autenticacion());

        GestoPagoPagoException error = assertThrows(GestoPagoPagoException.class, () -> gateway.enviar("firmada"));

        assertEquals(GestoPagoPagoException.Tipo.AUTENTICACION, error.getTipo());
        verify(pagosClient, times(2)).enviar(any(), any());
        verify(tokenService, times(1)).renovarToken();
    }

    @Test
    void siLaRenovacionDevuelveUnTokenEnBlancoFallaSinReintentar() {
        when(tokenService.renovarToken()).thenReturn(token(" "));
        when(pagosClient.enviar(any(), any())).thenThrow(autenticacion());

        assertThrows(GestoPagoAuthException.class, () -> gateway.enviar("firmada"));

        verify(pagosClient, times(1)).enviar(any(), any());
    }

    @Test
    void siLaRenovacionDevuelveNuloFallaSinReintentar() {
        when(tokenService.renovarToken()).thenReturn(null);
        when(pagosClient.enviar(any(), any())).thenThrow(autenticacion());

        assertThrows(GestoPagoAuthException.class, () -> gateway.enviar("firmada"));

        verify(pagosClient, times(1)).enviar(any(), any());
    }

    @Test
    void otrosErroresDelProveedorSePropaganSinRenovarElToken() {
        GestoPagoPagoException timeout = new GestoPagoPagoException("timeout",
                GestoPagoPagoException.Tipo.TIMEOUT, null, true, null);
        when(pagosClient.enviar(any(), any())).thenThrow(timeout);

        assertSame(timeout, assertThrows(GestoPagoPagoException.class, () -> gateway.enviar("firmada")));

        verify(tokenService, never()).renovarToken();
        verify(pagosClient, times(1)).enviar(any(), any());
    }

    @Test
    void sinTokenActivoNoSeLlamaAGestoPago() {
        when(tokenService.obtenerTokenActivo(10, "DISP")).thenReturn(Optional.empty());

        assertThrows(GestoPagoAuthException.class, () -> gateway.enviar("firmada"));

        verifyNoInteractions(pagosClient);
    }

    @Test
    void conTokenEnBlancoNoSeLlamaAGestoPago() {
        when(tokenService.obtenerTokenActivo(10, "DISP")).thenReturn(Optional.of(token("  ")));

        assertThrows(GestoPagoAuthException.class, () -> gateway.confirmar("firmada"));

        verifyNoInteractions(pagosClient);
    }

    private GestoPagoPagoException autenticacion() {
        return new GestoPagoPagoException("403", GestoPagoPagoException.Tipo.AUTENTICACION, 403, false, null);
    }

    private GestoPagoToken token(String valor) {
        GestoPagoToken token = new GestoPagoToken();
        token.setToken(valor);
        return token;
    }
}
