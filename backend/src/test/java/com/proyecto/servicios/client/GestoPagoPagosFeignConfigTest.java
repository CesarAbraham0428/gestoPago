package com.proyecto.servicios.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import feign.RequestTemplate;
import org.junit.jupiter.api.Test;

class GestoPagoPagosFeignConfigTest {

    private final GestoPagoPagosFeignConfig config = new GestoPagoPagosFeignConfig();

    @Test
    void agregaLaApiKeySinEspacios() {
        RequestTemplate plantilla = new RequestTemplate();

        config.gestoPagoApiKeyInterceptor(" llave ").apply(plantilla);

        assertEquals("llave", plantilla.headers().get("X-API-Key").iterator().next());
    }

    @Test
    void sinApiKeyNoAgregaLaCabecera() {
        for (String vacia : new String[] {"", "   ", null}) {
            RequestTemplate plantilla = new RequestTemplate();

            config.gestoPagoApiKeyInterceptor(vacia).apply(plantilla);

            assertFalse(plantilla.headers().containsKey("X-API-Key"));
        }
    }
}
