package com.proyecto.servicios.client;

import com.proyecto.servicios.model.gestopago.GestoPagoCatalogResponse;
import feign.Request;
import feign.Response;
import feign.RetryableException;
import feign.FeignException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GestoPagoCatalogClientTest {

    private static final String TOKEN = "token-de-prueba-no-real";

    @Mock
    private GestoPagoCatalogFeignClient feignClient;

    private GestoPagoCatalogClient client;

    @BeforeEach
    void setUp() {
        client = new GestoPagoCatalogClient(feignClient);
    }

    @Test
    void consultarCatalogo_enviaBearerTokenYDeserializaXml() {
        when(feignClient.consultarCatalogo("Bearer " + TOKEN)).thenReturn(respuesta(200, validXml()));

        GestoPagoCatalogResponse result = client.consultarCatalogo(TOKEN);

        assertEquals("01", result.getMensaje().getCodigo());
        assertEquals(1, result.getProductos().size());
        assertEquals("internet", result.getProductos().get(0).getProducto());
    }

    @Test
    void consultarCatalogo_xmlInvalidoLanzaExcepcionDeIntegracionConCausa() {
        when(feignClient.consultarCatalogo("Bearer " + TOKEN))
                .thenReturn(respuesta(200, "<RESPONSE><PRODUCTOS>"));

        GestoPagoCatalogException e = assertThrows(GestoPagoCatalogException.class,
                () -> client.consultarCatalogo(TOKEN));

        assertEquals(GestoPagoCatalogException.Tipo.XML, e.getTipo());
        assertNotNull(e.getCause());
        assertFalse(e.getMessage().contains(TOKEN));
    }

    @Test
    void consultarCatalogo_respuestaVaciaSeTraduce() {
        when(feignClient.consultarCatalogo("Bearer " + TOKEN)).thenReturn(respuesta(200, ""));

        GestoPagoCatalogException e = assertThrows(GestoPagoCatalogException.class,
                () -> client.consultarCatalogo(TOKEN));

        assertEquals(GestoPagoCatalogException.Tipo.RESPUESTA_VACIA, e.getTipo());
    }

    @ParameterizedTest
    @ValueSource(ints = {401, 403})
    void consultarCatalogo_rechazoDeAutenticacionSeTraduce(int status) {
        when(feignClient.consultarCatalogo("Bearer " + TOKEN))
                .thenReturn(respuesta(status, "error"));

        GestoPagoCatalogException e = assertThrows(GestoPagoCatalogException.class,
                () -> client.consultarCatalogo(TOKEN));

        assertEquals(GestoPagoCatalogException.Tipo.AUTENTICACION, e.getTipo());
        assertEquals(status, e.getStatusHttp());
        assertFalse(e.getMessage().contains(TOKEN));
    }

    @Test
    void consultarCatalogo_errorHttpNoExitosoSeTraduce() {
        when(feignClient.consultarCatalogo("Bearer " + TOKEN))
                .thenReturn(respuesta(502, "error"));

        GestoPagoCatalogException e = assertThrows(GestoPagoCatalogException.class,
                () -> client.consultarCatalogo(TOKEN));

        assertEquals(GestoPagoCatalogException.Tipo.HTTP, e.getTipo());
        assertEquals(502, e.getStatusHttp());
    }

    @Test
    void consultarCatalogo_errorHttpLanzadoPorFeignSeTraduce() {
        when(feignClient.consultarCatalogo("Bearer " + TOKEN))
                .thenThrow(FeignException.errorStatus("consultarCatalogo", respuesta(503, "error")));

        GestoPagoCatalogException e = assertThrows(GestoPagoCatalogException.class,
                () -> client.consultarCatalogo(TOKEN));

        assertEquals(GestoPagoCatalogException.Tipo.HTTP, e.getTipo());
        assertEquals(503, e.getStatusHttp());
        assertFalse(e.getMessage().contains(TOKEN));
    }

    @Test
    void consultarCatalogo_timeoutDeRedSeTraduceConCausa() {
        when(feignClient.consultarCatalogo("Bearer " + TOKEN))
                .thenThrow(new RetryableException(-1, "Read timed out", Request.HttpMethod.GET,
                        new SocketTimeoutException("Read timed out"), (Long) null, request()));

        GestoPagoCatalogException e = assertThrows(GestoPagoCatalogException.class,
                () -> client.consultarCatalogo(TOKEN));

        assertEquals(GestoPagoCatalogException.Tipo.TIMEOUT, e.getTipo());
        assertNotNull(e.getCause());
        assertFalse(e.getMessage().contains(TOKEN));
    }

    @Test
    void consultarCatalogo_errorDeComunicacionSeTraduce() {
        when(feignClient.consultarCatalogo("Bearer " + TOKEN))
                .thenThrow(new RetryableException(-1, "connection failed", Request.HttpMethod.GET,
                        new java.io.IOException("connection failed"), (Long) null, request()));

        GestoPagoCatalogException e = assertThrows(GestoPagoCatalogException.class,
                () -> client.consultarCatalogo(TOKEN));

        assertEquals(GestoPagoCatalogException.Tipo.COMUNICACION, e.getTipo());
    }

    private static Response respuesta(int status, String body) {
        return Response.builder()
                .status(status)
                .reason("test")
                .request(request())
                .body(body, StandardCharsets.UTF_8)
                .build();
    }

    private static Request request() {
        return Request.create(Request.HttpMethod.GET, "https://gestopago.test/catalog",
                Map.of(), null, StandardCharsets.UTF_8);
    }

    private static String validXml() {
        return "<RESPONSE><MENSAJE><CODIGO>01</CODIGO><TEXTO>OK</TEXTO></MENSAJE>"
                + "<PRODUCTOS><producto producto=\"internet\" servicio=\"Internet\" idServicio=\"10\" "
                + "idProducto=\"20\"/></PRODUCTOS></RESPONSE>";
    }
}
