package com.proyecto.servicios.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.proyecto.servicios.models.response.gestopago.GestoPagoPagosResponse;
import com.proyecto.servicios.utils.FirmaGestoPago;
import feign.Request;
import feign.Response;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Las respuestas XML son los ejemplos de la documentación de sendTx y confirmTx. */
class GestoPagoPagosClientTest {

    private static final String LLAVE = "HPo7OLqB4Fkk4E2yGOtwqw8H5fHR9kNx67OR4g4UdlA=";
    private static final String IV = "p5ldmBPdd/9pjC0bDC/nSg==";
    private final ObjectMapper mapper = new ObjectMapper();
    private final FirmaGestoPago firma = new FirmaGestoPago(mapper, LLAVE, IV);
    private final GestoPagoPagosFeignClient feign = mock(GestoPagoPagosFeignClient.class);
    private final GestoPagoPagosClient client = new GestoPagoPagosClient(feign, mapper, firma, "signed");

    @Test
    void interpretaPagoExitosoDeServicio() {
        GestoPagoPagosResponse respuesta = client.interpretar("""
                <?xml version='1.0' encoding='UTF-8'?>
                <RESPONSE>
                    <ID_TX>291620941</ID_TX>
                    <NUM_AUTORIZACION>100074211</NUM_AUTORIZACION>
                    <SALDO>154997.91</SALDO>
                    <COMISION>6.0</COMISION>
                    <SALDO_F>154,997.91</SALDO_F>
                    <COMISION_F>6</COMISION_F>
                    <FECHA>25/Feb/2022 18:59:34</FECHA>
                    <MONTO>937.99</MONTO>
                    <MENSAJE>
                        <CODIGO>01</CODIGO>
                        <TEXTO>Operacion realizada con exito</TEXTO>
                        <REFERENCIA>25720381</REFERENCIA>
                    </MENSAJE>
                </RESPONSE>""", 200);

        assertTrue(respuesta.esExitoso());
        assertEquals(291620941L, respuesta.getIdTx());
        assertEquals("100074211", respuesta.getNumAutorizacion());
        assertEquals(new BigDecimal("6.0"), respuesta.getComision());
        assertEquals(new BigDecimal("937.99"), respuesta.getMonto());
        assertEquals("25720381", respuesta.getReferencia());
        assertEquals("25/Feb/2022 18:59:34", respuesta.getFecha());
        assertEquals("Operacion realizada con exito", respuesta.getMensaje());
        assertTrue(respuesta.getRespuestaOriginal().contains("<ID_TX>291620941</ID_TX>"));
    }

    @Test
    void interpretaTarjetaDeRegaloConPinYLegendEnCdata() {
        GestoPagoPagosResponse respuesta = client.interpretar("""
                <?xml version='1.0' encoding='UTF-8'?>
                <RESPONSE>
                    <ID_TX>291676393</ID_TX>
                    <NUM_AUTORIZACION>65765544</NUM_AUTORIZACION>
                    <SALDO>1.5469791E5</SALDO>
                    <MONTO>300.0</MONTO>
                    <MENSAJE>
                        <CODIGO>01</CODIGO>
                        <PIN>40919958</PIN>
                        <legend>
                            <![CDATA[Para comenzar a ver: <br>1. Visita www.netflix.com/codigo]]>
                        </legend>
                        <TEXTO>Operacion realizada con exito</TEXTO>
                        <REFERENCIA></REFERENCIA>
                    </MENSAJE>
                </RESPONSE>""", 200);

        assertEquals("40919958", respuesta.getPin());
        assertEquals("Para comenzar a ver: <br>1. Visita www.netflix.com/codigo", respuesta.getLegend());
        assertEquals(0, new BigDecimal("154697.91").compareTo(respuesta.getSaldo()));
        assertNull(respuesta.getReferencia());
    }

    @Test
    void interpretaCodigosDeErrorDeSendTx() {
        GestoPagoPagosResponse saldo = client.interpretar(error("02", "Saldo insuficiente"), 200);
        GestoPagoPagosResponse timeout = client.interpretar(
                error("82", "Tiempo de espera alcanzado, la transacción no ha sido registrada"), 200);

        assertTrue(saldo.esSaldoInsuficiente());
        assertEquals("-1", saldo.getNumAutorizacion());
        assertTrue(timeout.esCodigo(GestoPagoPagosResponse.CODIGO_TIEMPO_AGOTADO));
    }

    @Test
    void interpretaConfirmTxConIdTxDentroDeMensaje() {
        GestoPagoPagosResponse aplicada = client.interpretar("""
                <RESPONSE>
                    <NUM_AUTORIZACION>100024988</NUM_AUTORIZACION>
                    <MENSAJE>
                        <CODIGO>06</CODIGO>
                        <TEXTO>La transaccion fue aplicada exitosamente.</TEXTO>
                        <ID_TX>291437188</ID_TX>
                    </MENSAJE>
                </RESPONSE>""", 200);

        assertTrue(aplicada.esCodigo(GestoPagoPagosResponse.CODIGO_CONFIRMADO_APLICADO));
        assertEquals(291437188L, aplicada.getIdTx());
    }

    @Test
    void enviaFormularioConRandomYSignedYElToken() throws Exception {
        when(feign.enviarTransaccion(anyString(), anyMap())).thenReturn(respuestaHttp(200, error("02", "x")));

        client.enviar("tok", "CIFRADO");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, ?>> formulario = ArgumentCaptor.forClass(Map.class);
        verify(feign).enviarTransaccion(eq("Bearer tok"), formulario.capture());
        assertEquals("CIFRADO", formulario.getValue().get("signed"));
        assertNotNull(formulario.getValue().get("random"));
    }

    @Test
    void tokenVencidoResponde403YSeClasificaComoAutenticacion() {
        when(feign.enviarTransaccion(anyString(), anyMap())).thenReturn(respuestaHttp(403,
                "{\"success\": false, \"token\": \"EXPIRED\"}"));

        GestoPagoPagoException error = assertThrows(GestoPagoPagoException.class,
                () -> client.enviar("tok", "CIFRADO"));

        assertEquals(GestoPagoPagoException.Tipo.AUTENTICACION, error.getTipo());
        assertFalse(error.isResultadoIncierto());
    }

    @Test
    void error5xxEsIncierto() {
        when(feign.enviarTransaccion(anyString(), anyMap())).thenReturn(respuestaHttp(502, ""));

        assertTrue(assertThrows(GestoPagoPagoException.class, () -> client.enviar("tok", "C"))
                .isResultadoIncierto());
    }

    @Test
    void respuestaSinCodigoEsIncierta() {
        GestoPagoPagoException error = assertThrows(GestoPagoPagoException.class,
                () -> client.interpretar("<RESPONSE><ID_TX>1</ID_TX></RESPONSE>", 200));

        assertTrue(error.isResultadoIncierto());
        assertEquals(GestoPagoPagoException.Tipo.RESPUESTA_INVALIDA, error.getTipo());
    }

    @Test
    void rechazaXmlConDoctype() {
        assertThrows(GestoPagoPagoException.class, () -> client.interpretar(
                "<!DOCTYPE x [<!ENTITY e SYSTEM \"file:///etc/passwd\">]><R><CODIGO>&e;</CODIGO></R>", 200));
    }

    private String error(String codigo, String texto) {
        return "<RESPONSE><NUM_AUTORIZACION>-1</NUM_AUTORIZACION><MENSAJE><CODIGO>" + codigo
                + "</CODIGO><TEXTO>" + texto + "</TEXTO></MENSAJE></RESPONSE>";
    }

    private Response respuestaHttp(int status, String cuerpo) {
        Request request = Request.create(Request.HttpMethod.POST, "https://gestopago/sendTx.do",
                new HashMap<>(), null, StandardCharsets.UTF_8, null);
        return Response.builder().status(status).request(request)
                .body(cuerpo, StandardCharsets.UTF_8).headers(new HashMap<>()).build();
    }
}
