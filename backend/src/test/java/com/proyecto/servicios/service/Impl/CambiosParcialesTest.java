package com.proyecto.servicios.service.Impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.proyecto.servicios.service.exception.ValidacionException;
import org.junit.jupiter.api.Test;

class CambiosParcialesTest {

    private final ObjectMapper mapper = new ObjectMapper();

    private ObjectNode json(String texto) throws Exception {
        return (ObjectNode) mapper.readTree(texto);
    }

    @Test
    void fusionaCamposSimplesYObjetosAnidadosConservandoLoOmitido() throws Exception {
        ObjectNode destino = json("{\"nombre\":\"Ana\",\"domicilio\":{\"calle\":\"Reforma\",\"cp\":\"06000\"}}");

        CambiosParciales.combinar(destino, json("{\"nombre\":\"Luisa\",\"domicilio\":{\"calle\":\"Insurgentes\"}}"));

        assertEquals("Luisa", destino.get("nombre").asText());
        assertEquals("Insurgentes", destino.get("domicilio").get("calle").asText());
        assertEquals("06000", destino.get("domicilio").get("cp").asText());
    }

    @Test
    void unValorNuloOUnTipoDistintoReemplazaAlOriginal() throws Exception {
        ObjectNode destino = json("{\"a\":{\"x\":1},\"b\":\"texto\"}");

        CambiosParciales.combinar(destino, json("{\"a\":5,\"b\":null}"));

        assertEquals(5, destino.get("a").asInt());
        assertTrue(destino.get("b").isNull());
    }

    @Test
    void rechazaCamposQueNoExistenIndicandoSuRuta() throws Exception {
        ObjectNode destino = json("{\"domicilio\":{\"calle\":\"x\"}}");

        ValidacionException nivel1 = assertThrows(ValidacionException.class,
                () -> CambiosParciales.combinar(destino, json("{\"curp\":\"X\"}")));
        ValidacionException nivel2 = assertThrows(ValidacionException.class,
                () -> CambiosParciales.combinar(destino, json("{\"domicilio\":{\"id\":1}}")));

        assertEquals("Campo no permitido en actualización: curp", nivel1.getMessage());
        assertEquals("Campo no permitido en actualización: domicilio.id", nivel2.getMessage());
    }
}
