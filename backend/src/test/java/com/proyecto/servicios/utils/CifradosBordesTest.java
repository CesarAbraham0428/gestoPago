package com.proyecto.servicios.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class CifradosBordesTest {

    private static final String LLAVE = "HPo7OLqB4Fkk4E2yGOtwqw8H5fHR9kNx67OR4g4UdlA=";
    private static final String IV = "p5ldmBPdd/9pjC0bDC/nSg==";

    @Test
    void elIvDebeExistirEstarEnBase64YMedir16Bytes() {
        assertThrows(IllegalStateException.class, () -> Encriptar.cifrar("x", null, LLAVE));
        assertThrows(IllegalStateException.class, () -> Encriptar.cifrar("x", "  ", LLAVE));
        assertThrows(IllegalStateException.class, () -> Encriptar.cifrar("x", "no es base64!!", LLAVE));
        String ivCorto = Base64.getEncoder().encodeToString(new byte[8]);
        IllegalStateException error =
                assertThrows(IllegalStateException.class, () -> Encriptar.cifrar("x", ivCorto, LLAVE));
        assertTrue(error.getMessage().contains("16 bytes"));
    }

    @Test
    void descifrarRechazaBase64InvalidoYDatosQueNoCorrespondenALaLlave() {
        assertThrows(IllegalStateException.class, () -> Encriptar.descifrar("%%%", IV, LLAVE));
        String otroTexto = Base64.getEncoder().encodeToString(new byte[16]);
        assertThrows(IllegalStateException.class, () -> Encriptar.descifrar(otroTexto, IV, LLAVE));
    }

    @Test
    void descifrarIgnoraEspaciosAlrededorDelTexto() {
        String cifrado = Encriptar.cifrar("hola", IV, LLAVE);

        assertEquals("hola", Encriptar.descifrar("  " + cifrado + "\n", IV, LLAVE));
    }

    @Test
    void laFirmaDescifraLoQueCifraYNoSerializaObjetosInvalidos() {
        FirmaGestoPago firma = new FirmaGestoPago(new ObjectMapper(), LLAVE, IV);

        String firmado = firma.firmar(java.util.Map.of("a", 1));

        assertEquals("{\"a\":1}", firma.descifrar(firmado));
        assertThrows(IllegalStateException.class, () -> firma.serializar(new Object()));
    }

    @Test
    void elConvertidorDeTokensConservaLosNulosYRechazaDatosAjenos() {
        String llave = Base64.getEncoder().encodeToString(new byte[32]);
        CifradoTokenConverter convertidor = new CifradoTokenConverter(llave);

        assertNull(convertidor.convertToDatabaseColumn(null));
        assertNull(convertidor.convertToEntityAttribute(null));
        String columna = convertidor.convertToDatabaseColumn("token");
        assertNotNull(columna);
        assertEquals("token", convertidor.convertToEntityAttribute(columna));

        String corrupta = Base64.getEncoder().encodeToString(new byte[40]);
        assertThrows(IllegalStateException.class, () -> convertidor.convertToEntityAttribute(corrupta));
        String cortaLlave = Base64.getEncoder().encodeToString(new byte[16]);
        assertThrows(IllegalStateException.class, () -> new CifradoTokenConverter(cortaLlave));
    }
}
