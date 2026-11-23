package com.proyecto.servicios.service.Impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.proyecto.servicios.service.exception.ValidacionException;
import java.util.Iterator;
import java.util.Map;

/** Aplica un PATCH (fusión de objetos JSON) permitiendo únicamente campos que ya existen en el destino. */
final class CambiosParciales {

    private CambiosParciales() {
    }

    static void combinar(ObjectNode destino, JsonNode cambios) {
        combinar(destino, cambios, "");
    }

    private static void combinar(ObjectNode destino, JsonNode cambios, String prefijo) {
        Iterator<Map.Entry<String, JsonNode>> campos = cambios.fields();
        while (campos.hasNext()) {
            Map.Entry<String, JsonNode> campo = campos.next();
            String nombre = campo.getKey();
            JsonNode actual = destino.get(nombre);
            if (actual == null) {
                throw new ValidacionException("Campo no permitido en actualización: " + prefijo + nombre);
            }
            if (actual.isObject() && campo.getValue().isObject()) {
                combinar((ObjectNode) actual, campo.getValue(), prefijo + nombre + ".");
            } else {
                destino.set(nombre, campo.getValue());
            }
        }
    }
}
