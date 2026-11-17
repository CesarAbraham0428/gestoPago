package com.proyecto.servicios.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.proyecto.servicios.client.GestoPagoPagoException.Tipo;
import com.proyecto.servicios.models.response.gestopago.GestoPagoPagosResponse;
import com.proyecto.servicios.utils.FirmaGestoPago;
import feign.FeignException;
import feign.Request;
import feign.RetryableException;
import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ErroresYParserPagosTest {

    private static final String LLAVE = "HPo7OLqB4Fkk4E2yGOtwqw8H5fHR9kNx67OR4g4UdlA=";
    private static final String IV = "p5ldmBPdd/9pjC0bDC/nSg==";
    private final ObjectMapper mapper = new ObjectMapper();
    private final FirmaGestoPago firma = new FirmaGestoPago(mapper, LLAVE, IV);
    private final RespuestaPagoParser parser = new RespuestaPagoParser(mapper, firma);

    private static Request request() {
        return Request.create(Request.HttpMethod.POST, "https://gestopago.test/x", Map.of(), null,
                StandardCharsets.UTF_8, null);
    }

    private static FeignException sinRespuesta(Throwable causa) {
        return new RetryableException(-1, "fallo", Request.HttpMethod.POST, causa, (Long) null, request());
    }

    // ---------- clasificación de errores ----------

    @ParameterizedTest
    @CsvSource({"401,AUTENTICACION,false", "403,AUTENTICACION,false", "400,HTTP,false", "408,HTTP,true",
            "500,HTTP,true", "503,HTTP,true"})
    void losEstadosHttpSeClasificanPorCerteza(int status, Tipo tipo, boolean incierto) {
        GestoPagoPagoException error = ErroresGestoPagoPagos.errorHttp(status, null);

        assertEquals(tipo, error.getTipo());
        assertEquals(incierto, error.isResultadoIncierto());
        assertEquals(status, error.getStatusHttp());
    }

    @Test
    void unEstadoHttpDeFeignSeTraduceConSuEstado() {
        FeignException e = FeignException.errorStatus("m", feign.Response.builder().status(502).reason("x")
                .request(request()).headers(Map.of()).build());

        GestoPagoPagoException error = ErroresGestoPagoPagos.traducirFeign(e);

        assertEquals(Tipo.HTTP, error.getTipo());
        assertTrue(error.isResultadoIncierto());
    }

    @Test
    void sinConexionLaPeticionNuncaLlegoYElResultadoNoEsIncierto() {
        for (Throwable causa : new Throwable[] {new ConnectException("x"), new UnknownHostException("h"),
                new SocketTimeoutException("connect timed out")}) {
            GestoPagoPagoException error = ErroresGestoPagoPagos.traducirFeign(sinRespuesta(causa));

            assertEquals(Tipo.COMUNICACION, error.getTipo(), causa.toString());
            assertFalse(error.isResultadoIncierto());
            assertNull(error.getStatusHttp());
        }
    }

    @Test
    void unTimeoutDeLecturaEsIncierto() {
        GestoPagoPagoException error = ErroresGestoPagoPagos
                .traducirFeign(sinRespuesta(new SocketTimeoutException("Read timed out")));
        GestoPagoPagoException sinMensaje = ErroresGestoPagoPagos
                .traducirFeign(sinRespuesta(new SocketTimeoutException()));

        assertEquals(Tipo.TIMEOUT, error.getTipo());
        assertTrue(error.isResultadoIncierto());
        assertEquals(Tipo.TIMEOUT, sinMensaje.getTipo());
    }

    @Test
    void otroFalloDeLaConexionEsUnaComunicacionInterrumpidaIncierta() {
        GestoPagoPagoException error = ErroresGestoPagoPagos.traducirFeign(sinRespuesta(new IOException("reset")));

        assertEquals(Tipo.COMUNICACION, error.getTipo());
        assertTrue(error.isResultadoIncierto());
    }

    @Test
    void laCausaDeRedSeBuscaEnTodaLaCadena() {
        assertTrue(CausasRed.contieneTimeout(new RuntimeException("x", new SocketTimeoutException())));
        assertTrue(CausasRed.contieneTimeout(new RuntimeException("Request TIMEOUT")));
        assertTrue(CausasRed.contieneTimeout(new RuntimeException("Read timed out")));
        assertFalse(CausasRed.contieneTimeout(new RuntimeException((String) null)));
        assertFalse(CausasRed.contieneTimeout(new RuntimeException("otra cosa")));
        assertTrue(CausasRed.contieneTimeout(new TimeoutPersonalizado()));
    }

    static class TimeoutPersonalizado extends RuntimeException {
    }

    // ---------- interpretación de respuestas ----------

    @Test
    void unaRespuestaVaciaONulaEsInvalida() {
        for (String cuerpo : new String[] {null, "", "   "}) {
            GestoPagoPagoException error = assertThrows(GestoPagoPagoException.class,
                    () -> parser.interpretar(cuerpo, 200));
            assertEquals(Tipo.RESPUESTA_INVALIDA, error.getTipo());
            assertTrue(error.isResultadoIncierto());
        }
    }

    @Test
    void interpretaJsonConValoresAnidadosYArreglosIgnorados() {
        GestoPagoPagosResponse respuesta = parser.interpretar(
                "{\"id_tx\":\"12\",\"extra\":[1,2],\"nulo\":null,\"MENSAJE\":{\"codigo\":\"01\",\"texto\":\"OK\"}}",
                200);

        assertTrue(respuesta.esExitoso());
        assertEquals(12L, respuesta.getIdTx());
        assertEquals("OK", respuesta.getMensaje());
    }

    @Test
    void interpretaUnaRespuestaCifradaConLaLlaveDelDistribuidor() {
        String xml = "<RESPONSE><MENSAJE><CODIGO>01</CODIGO></MENSAJE></RESPONSE>";

        GestoPagoPagosResponse respuesta = parser.interpretar(firma.firmarJson(xml), 200);

        assertTrue(respuesta.esExitoso());
    }

    @Test
    void unTextoQueNoEsXmlNiJsonNiCifradoEsInvalido() {
        GestoPagoPagoException error =
                assertThrows(GestoPagoPagoException.class, () -> parser.interpretar("texto-plano", 200));

        assertEquals("La respuesta de GestoPago no se pudo interpretar", error.getMessage());
    }

    @Test
    void unJsonOXmlMalFormadoEsInvalido() {
        assertThrows(GestoPagoPagoException.class, () -> parser.interpretar("{no-json", 200));
        assertThrows(GestoPagoPagoException.class, () -> parser.interpretar("<RESPONSE><A>", 200));
    }

    @Test
    void unCodigoEnBlancoEsInvalido() {
        GestoPagoPagoException error = assertThrows(GestoPagoPagoException.class,
                () -> parser.interpretar("<RESPONSE><CODIGO>  </CODIGO></RESPONSE>", 200));

        assertEquals("La respuesta de GestoPago no incluye CODIGO", error.getMessage());
    }

    @Test
    void losNumerosInvalidosSeIgnoranYLosVaciosSonNulos() {
        GestoPagoPagosResponse respuesta = parser.interpretar("""
                <RESPONSE><CODIGO>01</CODIGO><ID_TX>1.5</ID_TX><SALDO>$1,234.50</SALDO>
                <COMISION>abc</COMISION><MONTO></MONTO><MENSAJE>texto directo</MENSAJE></RESPONSE>""", 200);

        assertNull(respuesta.getIdTx());
        assertEquals(0, new java.math.BigDecimal("1234.50").compareTo(respuesta.getSaldo()));
        assertNull(respuesta.getComision());
        assertNull(respuesta.getMonto());
        assertEquals("texto directo", respuesta.getMensaje());
    }
}
