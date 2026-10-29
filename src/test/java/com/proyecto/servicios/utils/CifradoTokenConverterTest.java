package com.proyecto.servicios.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CifradoTokenConverterTest {

    private static final String LLAVE = "HPo7OLqB4Fkk4E2yGOtwqw8H5fHR9kNx67OR4g4UdlA=";
    private final CifradoTokenConverter converter = new CifradoTokenConverter(LLAVE);

    @Test
    void elTokenNoSeGuardaEnClaroYSeRecuperaIgual() {
        String columna = converter.convertToDatabaseColumn("eyJhbGciOiJIUzI1NiJ9.token");

        assertFalse(columna.contains("eyJhbGci"));
        assertNotEquals(columna, converter.convertToDatabaseColumn("eyJhbGciOiJIUzI1NiJ9.token"),
                "cada cifrado usa un IV distinto");
        assertEquals("eyJhbGciOiJIUzI1NiJ9.token", converter.convertToEntityAttribute(columna));
    }

    @Test
    void unValorAlteradoSeRechaza() {
        String columna = converter.convertToDatabaseColumn("token");
        String alterada = columna.substring(0, columna.length() - 4) + "AAA=";

        assertThrows(IllegalStateException.class, () -> converter.convertToEntityAttribute(alterada));
    }

    @Test
    void llaveDeLongitudIncorrectaNoArranca() {
        assertThrows(IllegalStateException.class, () -> new CifradoTokenConverter("p5ldmBPdd/9pjC0bDC/nSg=="));
    }
}
