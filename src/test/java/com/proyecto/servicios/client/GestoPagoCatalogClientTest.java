package com.proyecto.servicios.client;

import com.proyecto.servicios.model.gestopago.GestoPagoCatalogResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;

import java.net.SocketTimeoutException;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

class GestoPagoCatalogClientTest {

    private static final String ENDPOINT = "https://gestopago.test/catalog";
    private static final String TOKEN = "token-de-prueba-no-real";

    @Test
    void consultarCatalogo_enviaBearerTokenYDeserializaXml() {
        ClientHarness harness = newClient();
        harness.server().expect(requestTo(ENDPOINT))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("Authorization", "Bearer " + TOKEN))
                .andRespond(withSuccess(validXml(), MediaType.APPLICATION_XML));

        GestoPagoCatalogResponse result = harness.client().consultarCatalogo(TOKEN);

        assertEquals("01", result.getMensaje().getCodigo());
        assertEquals(1, result.getProductos().size());
        assertEquals("internet", result.getProductos().get(0).getProducto());
        harness.server().verify();
    }

    @Test
    void consultarCatalogo_xmlInvalidoLanzaExcepcionDeIntegracionConCausa() {
        ClientHarness harness = newClient();
        harness.server().expect(requestTo(ENDPOINT))
                .andRespond(withSuccess("<RESPONSE><PRODUCTOS>", MediaType.APPLICATION_XML));

        GestoPagoCatalogException exception = assertThrows(
                GestoPagoCatalogException.class,
                () -> harness.client().consultarCatalogo(TOKEN));

        assertTrue(exception.getMessage().contains("XML"));
        assertTrue(exception.getMessage().contains("válida"));
        assertFalse(exception.getMessage().contains(TOKEN));
        assertTrue(exception.getCause() != null);
        assertEquals(GestoPagoCatalogException.Tipo.XML, exception.getTipo());
        harness.server().verify();
    }

    @ParameterizedTest
    @ValueSource(ints = {401, 403})
    void consultarCatalogo_errorDeAutenticacionOAutorizacionSeTraduceAExcepcionDeIntegracion(int status) {
        ClientHarness harness = newClient();
        harness.server().expect(requestTo(ENDPOINT))
                .andExpect(header("Authorization", "Bearer " + TOKEN))
                .andRespond(withStatus(org.springframework.http.HttpStatus.valueOf(status)));

        GestoPagoCatalogException exception = assertThrows(
                GestoPagoCatalogException.class,
                () -> harness.client().consultarCatalogo(TOKEN));

        assertTrue(exception.getCause() instanceof RestClientResponseException);
        RestClientResponseException cause = (RestClientResponseException) exception.getCause();
        assertEquals(status, cause.getStatusCode().value());
        assertEquals(GestoPagoCatalogException.Tipo.AUTENTICACION, exception.getTipo());
        assertEquals(status, exception.getStatusHttp());
        assertFalse(exception.getMessage().contains(TOKEN));
        harness.server().verify();
    }

    @Test
    void consultarCatalogo_errorHttpNoExitosoSeTraduceAExcepcionDeIntegracion() {
        ClientHarness harness = newClient();
        harness.server().expect(requestTo(ENDPOINT))
                .andRespond(withStatus(org.springframework.http.HttpStatus.BAD_GATEWAY));

        GestoPagoCatalogException exception = assertThrows(
                GestoPagoCatalogException.class,
                () -> harness.client().consultarCatalogo(TOKEN));

        assertTrue(exception.getCause() instanceof RestClientResponseException);
        assertEquals(502, ((RestClientResponseException) exception.getCause()).getStatusCode().value());
        assertEquals(GestoPagoCatalogException.Tipo.HTTP, exception.getTipo());
        assertEquals(502, exception.getStatusHttp());
        assertFalse(exception.getMessage().contains(TOKEN));
        harness.server().verify();
    }

    @Test
    void consultarCatalogo_timeoutDeRedSeTraduceAExcepcionDeIntegracionConCausa() {
        ClientHarness harness = newClient();
        harness.server().expect(requestTo(ENDPOINT))
                .andRespond(request -> {
                    throw new ResourceAccessException(
                            "Read timed out",
                            new SocketTimeoutException("Read timed out"));
                });

        GestoPagoCatalogException exception = assertThrows(
                GestoPagoCatalogException.class,
                () -> harness.client().consultarCatalogo(TOKEN));

        assertTrue(exception.getCause() instanceof ResourceAccessException);
        assertTrue(exception.getCause().getCause() instanceof SocketTimeoutException);
        assertEquals(GestoPagoCatalogException.Tipo.TIMEOUT, exception.getTipo());
        assertFalse(exception.getMessage().contains(TOKEN));
        harness.server().verify();
    }

    private static ClientHarness newClient() {
        AtomicReference<RestTemplate> restTemplateReference = new AtomicReference<>();
        RestTemplateBuilder builder = new RestTemplateBuilder()
                .additionalCustomizers(restTemplateReference::set);
        GestoPagoCatalogClient client = new GestoPagoCatalogClient(
                builder,
                "https://gestopago.test",
                "/catalog",
                1_000,
                2_000);
        RestTemplate restTemplate = restTemplateReference.get();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        return new ClientHarness(client, server);
    }

    private static String validXml() {
        return "<RESPONSE>"
                + "<MENSAJE><CODIGO>01</CODIGO><TEXTO>OK</TEXTO></MENSAJE>"
                + "<PRODUCTOS><producto producto=\"internet\" servicio=\"Internet\" idServicio=\"10\" idProducto=\"20\"/>"
                + "</PRODUCTOS></RESPONSE>";
    }

    private record ClientHarness(GestoPagoCatalogClient client, MockRestServiceServer server) {
    }
}
