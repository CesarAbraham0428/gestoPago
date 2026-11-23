package com.proyecto.servicios.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.proyecto.servicios.client.GestoPagoPagoException;
import com.proyecto.servicios.entity.sf.Pago;
import com.proyecto.servicios.models.response.gestopago.GestoPagoPagosResponse;
import com.proyecto.servicios.utils.FirmaGestoPago;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ConciliacionPagoServiceTest {

    private static final String LLAVE = "HPo7OLqB4Fkk4E2yGOtwqw8H5fHR9kNx67OR4g4UdlA=";
    private static final String IV = "p5ldmBPdd/9pjC0bDC/nSg==";
    private static final Instant AHORA = Instant.parse("2026-10-19T19:15:26Z");
    private final PagoRegistroService registro = mock(PagoRegistroService.class);
    private final GestoPagoPagosGateway gateway = mock(GestoPagoPagosGateway.class);
    private final FirmaGestoPago firma = new FirmaGestoPago(new ObjectMapper(), LLAVE, IV);
    private final ConciliacionPagoService conciliacion = new ConciliacionPagoService(registro, firma, gateway,
            Clock.fixed(AHORA, ZoneOffset.UTC));

    @Test
    void buscaLosPagosDeLasUltimas24HorasConLasEsperasDeGestoPago() {
        when(registro.idsPorConfirmar(any(), any(), any())).thenReturn(List.of());

        conciliacion.conciliarPendientes();

        verify(registro).idsPorConfirmar(AHORA.minusSeconds(24 * 3600), AHORA.minusSeconds(62),
                AHORA.minusSeconds(5 * 60));
        verifyNoInteractions(gateway);
    }

    @Test
    void reenviaLaMismaPeticionAConfirmTxYApruebaSiYaSeAplico() {
        pagoPorConfirmar(80, "{\"upc\":\"ABC\"}");
        GestoPagoPagosResponse aplicada = respuesta("06");
        aplicada.setNumAutorizacion("100024988");
        when(gateway.confirmar(firma.firmarJson("{\"upc\":\"ABC\"}"))).thenReturn(aplicada);

        conciliacion.conciliarPendientes();

        verify(registro).confirmarAplicado(80, aplicada);
        verify(registro, never()).confirmarNoAplicado(any(), any());
    }

    @Test
    void codigo01DeConfirmTxTambienSeTomaComoAplicado() {
        pagoPorConfirmar(80, "{\"upc\":\"ABC\"}");
        GestoPagoPagosResponse exitosa = respuesta("01");
        when(gateway.confirmar(anyString())).thenReturn(exitosa);

        conciliacion.conciliarPendientes();

        verify(registro).confirmarAplicado(80, exitosa);
    }

    @Test
    void codigo06ConAutorizacionInvalidaNoEsConcluyente() {
        pagoPorConfirmar(80, "{\"upc\":\"ABC\"}");
        for (String autorizacion : new String[] {null, "0", "-1"}) {
            GestoPagoPagosResponse dudosa = respuesta("06");
            dudosa.setNumAutorizacion(autorizacion);
            when(gateway.confirmar(anyString())).thenReturn(dudosa);

            conciliacion.conciliarPendientes();
        }

        verify(registro, never()).confirmarAplicado(any(), any());
        verify(registro, never()).confirmarNoAplicado(any(), any());
    }

    @Test
    void devuelveElSaldoSiConfirmTxRespondeNoAplicada() {
        pagoPorConfirmar(81, "{\"upc\":\"DEF\"}");
        GestoPagoPagosResponse noAplicada = respuesta("70");
        noAplicada.setNumAutorizacion("0");
        when(gateway.confirmar(anyString())).thenReturn(noAplicada);

        conciliacion.conciliarPendientes();

        verify(registro).confirmarNoAplicado(81, noAplicada);
        verify(registro, never()).confirmarAplicado(any(), any());
    }

    @Test
    void otroCodigoDeConfirmTxDejaElPagoParaReintentar() {
        pagoPorConfirmar(81, "{\"upc\":\"DEF\"}");
        when(gateway.confirmar(anyString())).thenReturn(respuesta("99"));

        conciliacion.conciliarPendientes();

        verify(registro, never()).confirmarAplicado(any(), any());
        verify(registro, never()).confirmarNoAplicado(any(), any());
    }

    @Test
    void sinPeticionOriginalNoSeLlamaAGestoPago() {
        pagoPorConfirmar(83, null);

        conciliacion.conciliarPendientes();

        verifyNoInteractions(gateway);
        verify(registro, never()).confirmarAplicado(any(), any());
        verify(registro, never()).confirmarNoAplicado(any(), any());
    }

    @Test
    void continuaConLosDemasPagosSiUnoFalla() {
        when(registro.idsPorConfirmar(any(), any(), any())).thenReturn(List.of(82, 84));
        when(registro.buscarParaConfirmar(82)).thenReturn(pago(82, "{\"upc\":\"G\"}"));
        when(registro.buscarParaConfirmar(84)).thenReturn(pago(84, "{\"upc\":\"H\"}"));
        GestoPagoPagosResponse noAplicada = respuesta("70");
        when(gateway.confirmar(firma.firmarJson("{\"upc\":\"G\"}"))).thenThrow(new GestoPagoPagoException("timeout",
                GestoPagoPagoException.Tipo.TIMEOUT, null, true, null));
        when(gateway.confirmar(firma.firmarJson("{\"upc\":\"H\"}"))).thenReturn(noAplicada);

        assertDoesNotThrow(conciliacion::conciliarPendientes);

        verify(registro, never()).confirmarAplicado(eq(82), any());
        verify(registro, never()).confirmarNoAplicado(eq(82), any());
        verify(registro).confirmarNoAplicado(84, noAplicada);
    }

    private void pagoPorConfirmar(int id, String peticion) {
        when(registro.idsPorConfirmar(any(), any(), any())).thenReturn(List.of(id));
        when(registro.buscarParaConfirmar(id)).thenReturn(pago(id, peticion));
    }

    private Pago pago(int id, String peticion) {
        Pago pendiente = new Pago();
        pendiente.setId(id);
        pendiente.setPeticion(peticion);
        return pendiente;
    }

    private GestoPagoPagosResponse respuesta(String codigo) {
        GestoPagoPagosResponse respuesta = new GestoPagoPagosResponse();
        respuesta.setCodigo(codigo);
        respuesta.setIdTx(900L);
        return respuesta;
    }
}
