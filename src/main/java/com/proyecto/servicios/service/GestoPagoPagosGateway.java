package com.proyecto.servicios.service;

import com.proyecto.servicios.client.GestoPagoAuthException;
import com.proyecto.servicios.client.GestoPagoPagoException;
import com.proyecto.servicios.client.GestoPagoPagosClient;
import com.proyecto.servicios.entity.gestopago.GestoPagoToken;
import com.proyecto.servicios.models.response.gestopago.GestoPagoPagosResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.function.Function;

/**
 * Llamadas a sendTx.do, confirmTx.do y verifyReference.do con el token vigente de GestoPago.
 * Si GestoPago rechaza el token (vencido), lo renueva y reintenta una vez.
 */
@Component
@Slf4j
public class GestoPagoPagosGateway {

    private final GestoPagoPagosClient pagosClient;
    private final GestoPagoTokenService tokenService;
    private final Integer idDistribuidor;
    private final String codigoDispositivo;

    public GestoPagoPagosGateway(GestoPagoPagosClient pagosClient, GestoPagoTokenService tokenService,
            @Value("${gestopago.auth.id-distribuidor}") Integer idDistribuidor,
            @Value("${gestopago.auth.codigo-dispositivo}") String codigoDispositivo) {
        this.pagosClient = pagosClient;
        this.tokenService = tokenService;
        this.idDistribuidor = idDistribuidor;
        this.codigoDispositivo = codigoDispositivo;
    }

    public GestoPagoPagosResponse enviar(String peticionFirmada) {
        return conRenovacion(token -> pagosClient.enviar(token, peticionFirmada));
    }

    public GestoPagoPagosResponse confirmar(String peticionFirmada) {
        return conRenovacion(token -> pagosClient.confirmar(token, peticionFirmada));
    }

    public GestoPagoPagosResponse verificarReferencia(String peticionFirmada) {
        return conRenovacion(token -> pagosClient.verificarReferencia(token, peticionFirmada));
    }

    private GestoPagoPagosResponse conRenovacion(Function<String, GestoPagoPagosResponse> llamada) {
        try {
            return llamada.apply(tokenActivo());
        } catch (GestoPagoPagoException e) {
            if (e.getTipo() != GestoPagoPagoException.Tipo.AUTENTICACION) {
                throw e;
            }
            // Token vencido (403): la transacción no se procesó y es seguro reintentar con el mismo UPC.
            log.warn("GestoPago rechazó el token; se renovará y se reintentará una vez");
            GestoPagoToken renovado = tokenService.renovarToken();
            if (renovado == null || renovado.getToken() == null || renovado.getToken().isBlank()) {
                throw new GestoPagoAuthException("No fue posible renovar la autenticación de GestoPago",
                        GestoPagoAuthException.Tipo.RESPUESTA_INVALIDA, null, e);
            }
            return llamada.apply(renovado.getToken());
        }
    }

    private String tokenActivo() {
        return tokenService.obtenerTokenActivo(idDistribuidor, codigoDispositivo)
                .map(GestoPagoToken::getToken)
                .filter(token -> !token.isBlank())
                .orElseThrow(() -> new GestoPagoAuthException("No hay un token activo de GestoPago",
                        GestoPagoAuthException.Tipo.RESPUESTA_INVALIDA, null, null));
    }
}
