package com.proyecto.servicios.models.response;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GenericResponseTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void exitoContieneSoloCodigoMensajeYDatos() {
        var json = mapper.valueToTree(GenericResponse.exito("Consulta realizada", Map.of("id", 7)));
        assertEquals(3, json.size());
        assertEquals(0, json.get("codigo").asInt());
        assertEquals("Consulta realizada", json.get("mensaje").asText());
        assertEquals(7, json.get("datos").get("id").asInt());
        assertFalse(json.has("errores"));
    }

    @Test
    void exitoSinContenidoConservaDatosNull() {
        var json = mapper.valueToTree(GenericResponse.exito("Cliente desactivado", null));
        assertEquals(3, json.size());
        assertEquals(0, json.get("codigo").asInt());
        assertTrue(json.has("datos"));
        assertTrue(json.get("datos").isNull());
    }

    @Test
    void falloContieneSoloCodigoYMensaje() {
        var json = mapper.valueToTree(GenericResponse.error("Cliente no encontrado"));
        assertEquals(2, json.size());
        assertEquals(1, json.get("codigo").asInt());
        assertEquals("Cliente no encontrado", json.get("mensaje").asText());
        assertFalse(json.has("datos"));
        assertFalse(json.has("errores"));
    }
}
