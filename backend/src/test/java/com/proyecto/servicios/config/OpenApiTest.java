package com.proyecto.servicios.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import io.swagger.v3.oas.models.OpenAPI;
import org.junit.jupiter.api.Test;

class OpenApiTest {

    @Test
    void declaraElEsquemaBearerJwtYLoExigeEnTodasLasRutas() {
        OpenAPI api = new OpenApi().openAPI();

        assertEquals("JWT", api.getComponents().getSecuritySchemes().get("bearerAuth").getBearerFormat());
        assertNotNull(api.getSecurity().get(0).get("bearerAuth"));
    }
}
