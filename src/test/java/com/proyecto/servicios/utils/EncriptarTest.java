package com.proyecto.servicios.utils;

import java.util.Base64;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EncriptarTest {

    // Par de prueba publicado en la documentación de GestoPago (PuntoRed API Technical Specification).
    private static final String LLAVE = "HPo7OLqB4Fkk4E2yGOtwqw8H5fHR9kNx67OR4g4UdlA=";
    private static final String IV = "p5ldmBPdd/9pjC0bDC/nSg==";

    @Test
    void produceElResultadoEsperadoDeLaDocumentacion() {
        String json = "{'idProducto':582,'idServicio':133,'telefono':'5555555551',"
                + "'horaLocal':'20220101235900','upc':'test_UPC_83_182'}";

        assertEquals("+LWZ7icI60/DAI8ggv/SpDvJpFYeaYOZ5J7hPGK7g/87r9PRJfgFRBoBLYpBxW33ouvuwQPnhhKHXqzbiqLhMSH4gSH8"
                + "SMvW6PvHfeklNfTrZnbgWFWDPSy/nqFCP0B/gKavafHG0tW5UP4KPwyhECZK2pLT+I3qYAA85uQaAPQ=",
                Encriptar.AESCipher(Encriptar.Mode.ENCRYPT, json, IV, LLAVE));
    }

    @Test
    void cifraYDescifraConLaMismaLlave() {
        String json = "{\"idProducto\":1,\"referencia\":\"ÁÉÍ123\"}";

        String cifrado = Encriptar.AESCipher(Encriptar.Mode.ENCRYPT, json, IV, LLAVE);

        assertNotEquals(json, cifrado);
        assertEquals(json, Encriptar.AESCipher(Encriptar.Mode.DECRYPT, cifrado, IV, LLAVE));
    }

    @Test
    void llaveFaltanteOQueNoEsAes256FallaSinDevolverNull() {
        assertThrows(IllegalStateException.class, () -> Encriptar.AESCipher(Encriptar.Mode.ENCRYPT, "x", IV, ""));
        String llaveAes128 = Base64.getEncoder().encodeToString("0123456789abcdef".getBytes());
        assertThrows(IllegalStateException.class,
                () -> Encriptar.AESCipher(Encriptar.Mode.ENCRYPT, "x", IV, llaveAes128));
    }

    @Test
    void upcAleatorioTieneQuinceCaracteresAlfanumericos() {
        String upc = CadenaAleatoria.generar(15);
        assertTrue(upc.matches("[A-Z0-9]{15}"));
        assertNotEquals(upc, CadenaAleatoria.generar(15));
    }
}
