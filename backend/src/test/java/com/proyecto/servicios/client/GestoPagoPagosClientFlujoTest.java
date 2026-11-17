package com.proyecto.servicios.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.proyecto.servicios.client.GestoPagoPagoException.Tipo;
import com.proyecto.servicios.models.response.gestopago.GestoPagoPagosResponse;
import com.proyecto.servicios.utils.FirmaGestoPago;
import feign.Request;
import feign.Response;
import feign.RetryableException;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.net.SocketTimeoutException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.Test;

class GestoPagoPagosClientFlujoTest {

    private static final String EXITO =
            "<RESPONSE><ID_TX>7</ID_TX><MENSAJE><CODIGO>01</CODIGO></MENSAJE></RESPONSE>";

    private final ObjectMapper mapper = new ObjectMapper();
    private final FirmaGestoPago firma = new FirmaGestoPago(mapper, "HPo7OLqB4Fkk4E2yGOtwqw8H5fHR9kNx67OR4g4UdlA=",
            "p5ldmBPdd/9pjC0bDC/nSg==");
    private final GestoPagoPagosFeignClient feign = mock(GestoPagoPagosFeignClient.class);
    private final GestoPagoPagosClient client =
            new GestoPagoPagosClient(feign, new RespuestaPagoParser(mapper, firma), "signed");

    private static Request request() {
        return Request.create(Request.HttpMethod.POST, "https://gestopago.test/x", Map.of(), null,
                StandardCharsets.UTF_8, null);
    }

    private static Response respuesta(int status, String cuerpo) {
        return Response.builder().status(status).reason("x").request(request()).headers(Map.of())
                .body(cuerpo, StandardCharsets.UTF_8).build();
    }

    @Test
    void confirmarYVerificarUsanSusPropiasRutasConElMismoToken() {
        when(feign.confirmarTransaccion(anyString(), anyMap())).thenReturn(respuesta(200, EXITO));
        when(feign.verificarReferencia(anyString(), anyMap())).thenReturn(respuesta(200, EXITO));

        GestoPagoPagosResponse confirmada = client.confirmar("tok", "FIRMA");
        GestoPagoPagosResponse verificada = client.verificarReferencia("tok", "FIRMA");

        assertTrue(confirmada.esExitoso());
        assertEquals(7L, verificada.getIdTx());
        verify(feign).confirmarTransaccion(org.mockito.ArgumentMatchers.eq("Bearer tok"), anyMap());
        verify(feign).verificarReferencia(org.mockito.ArgumentMatchers.eq("Bearer tok"), anyMap());
    }

    @Test
    void unaRespuestaSinCuerpoEsInvalidaEIncierta() {
        when(feign.enviarTransaccion(anyString(), anyMap()))
                .thenReturn(Response.builder().status(200).reason("x").request(request()).headers(Map.of()).build());

        GestoPagoPagoException error =
                assertThrows(GestoPagoPagoException.class, () -> client.enviar("tok", "F"));

        assertEquals(Tipo.RESPUESTA_INVALIDA, error.getTipo());
        assertTrue(error.isResultadoIncierto());
    }

    @Test
    void unCuerpoIlegibleEsUnaRespuestaInvalida() {
        Response.Body roto = new Response.Body() {
            @Override
            public Integer length() {
                return null;
            }

            @Override
            public boolean isRepeatable() {
                return false;
            }

            @Override
            public InputStream asInputStream() {
                return new InputStream() {
                    @Override
                    public int read() throws IOException {
                        throw new IOException("cortado");
                    }
                };
            }

            @Override
            public Reader asReader(Charset charset) {
                return null;
            }

            @Override
            public void close() {
                // nada
            }
        };
        when(feign.enviarTransaccion(anyString(), anyMap()))
                .thenReturn(Response.builder().status(200).reason("x").request(request()).headers(Map.of())
                        .body(roto).build());

        GestoPagoPagoException error =
                assertThrows(GestoPagoPagoException.class, () -> client.enviar("tok", "F"));

        assertEquals("No se pudo leer la respuesta de GestoPago", error.getMessage());
        assertEquals(200, error.getStatusHttp());
    }

    @Test
    void unFalloDeRedSeTraduceYConservaElTipo() {
        when(feign.enviarTransaccion(anyString(), anyMap())).thenThrow(new RetryableException(-1, "x",
                Request.HttpMethod.POST, new SocketTimeoutException("Read timed out"), (Long) null, request()));

        GestoPagoPagoException error =
                assertThrows(GestoPagoPagoException.class, () -> client.enviar("tok", "F"));

        assertEquals(Tipo.TIMEOUT, error.getTipo());
    }

    @Test
    void unEstadoNoExitosoSeTraduceSinLeerElCuerpo() {
        when(feign.enviarTransaccion(anyString(), anyMap())).thenReturn(respuesta(500, "error"));

        GestoPagoPagoException error =
                assertThrows(GestoPagoPagoException.class, () -> client.enviar("tok", "F"));

        assertEquals(500, error.getStatusHttp());
        assertTrue(error.isResultadoIncierto());
    }

    @Test
    void unEstadoInformativoOUnaRedireccionTampocoSonExitosos() {
        when(feign.enviarTransaccion(anyString(), anyMap())).thenReturn(respuesta(199, ""), respuesta(302, ""));

        assertEquals(199, assertThrows(GestoPagoPagoException.class, () -> client.enviar("tok", "F")).getStatusHttp());
        assertEquals(302, assertThrows(GestoPagoPagoException.class, () -> client.enviar("tok", "F")).getStatusHttp());
    }
}
