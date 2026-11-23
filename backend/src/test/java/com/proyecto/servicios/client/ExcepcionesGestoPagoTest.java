package com.proyecto.servicios.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.proyecto.servicios.client.GestoPagoAuthException.Tipo;
import feign.FeignException;
import feign.Request;
import feign.Response;
import feign.RetryableException;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ExcepcionesGestoPagoTest {

    private static Request request() {
        return Request.create(Request.HttpMethod.POST, "https://gestopago.test/auth", Map.of(), null,
                StandardCharsets.UTF_8, null);
    }

    private static FeignException conEstado(int status) {
        return FeignException.errorStatus("m",
                Response.builder().status(status).reason("x").request(request()).headers(Map.of()).build());
    }

    private static FeignException sinRespuesta(Throwable causa) {
        return new RetryableException(-1, "fallo", Request.HttpMethod.POST, causa, (Long) null, request());
    }

    @Test
    void laAutenticacionSeClasificaPorEstadoYPorCausa() {
        assertEquals(Tipo.AUTENTICACION, GestoPagoAuthException.desde(conEstado(401)).getTipo());
        assertEquals(Tipo.AUTENTICACION, GestoPagoAuthException.desde(conEstado(403)).getTipo());
        GestoPagoAuthException http = GestoPagoAuthException.desde(conEstado(500));
        assertEquals(Tipo.HTTP, http.getTipo());
        assertEquals(500, http.getStatusHttp());

        GestoPagoAuthException timeout = GestoPagoAuthException.desde(sinRespuesta(new SocketTimeoutException()));
        assertEquals(Tipo.TIMEOUT, timeout.getTipo());
        assertNull(timeout.getStatusHttp());
        assertEquals(Tipo.COMUNICACION,
                GestoPagoAuthException.desde(sinRespuesta(new IOException("caída"))).getTipo());
    }

    @Test
    void lasExcepcionesDelCatalogoConservanTipoYEstado() {
        GestoPagoCatalogException conMensaje = new GestoPagoCatalogException("m");
        GestoPagoCatalogException conCausa = new GestoPagoCatalogException("m", new RuntimeException("c"));

        assertEquals(GestoPagoCatalogException.Tipo.RESPUESTA_INVALIDA, conMensaje.getTipo());
        assertEquals(GestoPagoCatalogException.Tipo.COMUNICACION, conCausa.getTipo());
        assertNull(conCausa.getStatusHttp());
    }
}
