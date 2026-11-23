package com.proyecto.servicios.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.OptionalLong;
import javax.crypto.Mac;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;

class TotpServiceTest {

    /** "12345678901234567890" en Base32: el secreto de los vectores de prueba del RFC 6238 (SHA-1). */
    private static final String SECRETO_RFC = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ";

    private static TotpService en(long segundos) {
        return new TotpService(Clock.fixed(Instant.ofEpochSecond(segundos), ZoneOffset.UTC));
    }

    @Test
    void coincideConLosVectoresDelRfc6238() {
        // Apéndice B del RFC (8 dígitos); los 6 dígitos son los últimos seis.
        long[][] vectores = {{59, 287082}, {1111111109L, 81804}, {1111111111L, 50471}, {1234567890L, 5924},
                {2000000000L, 279037}, {20000000000L, 353130}};
        for (long[] v : vectores) {
            assertEquals(String.format("%06d", v[1]), en(v[0]).codigo(SECRETO_RFC, v[0] / 30), "T=" + v[0]);
        }
    }

    @Test
    void verificaElCodigoActualYToleraUnPasoDeDesfase() {
        TotpService totp = en(1234567890L);
        long paso = totp.pasoActual();

        assertEquals(OptionalLong.of(paso), totp.verificar(SECRETO_RFC, totp.codigo(SECRETO_RFC, paso), 0));
        assertEquals(OptionalLong.of(paso - 1), totp.verificar(SECRETO_RFC, totp.codigo(SECRETO_RFC, paso - 1), 0));
        assertEquals(OptionalLong.of(paso + 1), totp.verificar(SECRETO_RFC, totp.codigo(SECRETO_RFC, paso + 1), 0));
        assertTrue(totp.verificar(SECRETO_RFC, totp.codigo(SECRETO_RFC, paso + 2), 0).isEmpty());
        assertTrue(totp.verificar(SECRETO_RFC, totp.codigo(SECRETO_RFC, paso - 2), 0).isEmpty());
    }

    @Test
    void unCodigoYaUsadoNoSeAceptaDeNuevo() {
        TotpService totp = en(1234567890L);
        long paso = totp.pasoActual();
        String codigo = totp.codigo(SECRETO_RFC, paso);

        assertTrue(totp.verificar(SECRETO_RFC, codigo, paso).isEmpty());
        assertTrue(totp.verificar(SECRETO_RFC, codigo, paso + 1).isEmpty());
        assertEquals(OptionalLong.of(paso), totp.verificar(SECRETO_RFC, codigo, paso - 1));
    }

    @Test
    void rechazaCodigosIncorrectosYEntradasNulas() {
        TotpService totp = en(1234567890L);
        String correcto = totp.codigo(SECRETO_RFC, totp.pasoActual());
        String otro = correcto.equals("000000") ? "000001" : "000000";

        assertTrue(totp.verificar(SECRETO_RFC, otro, 0).isEmpty());
        assertTrue(totp.verificar(null, correcto, 0).isEmpty());
        assertTrue(totp.verificar(SECRETO_RFC, null, 0).isEmpty());
    }

    @Test
    void elSecretoNuevoTiene32CaracteresBase32SinRellenoYNoSeRepite() {
        TotpService totp = en(0);
        String secreto = totp.nuevoSecreto();

        assertTrue(secreto.matches("[A-Z2-7]{32}"));
        assertNotEquals(secreto, totp.nuevoSecreto());
        assertEquals(6, totp.codigo(secreto, 1).length());
    }

    @Test
    void laUriLlevaEmisorSecretoYCorreoCodificado() {
        String uri = en(0).uri("ana+pago@correo.mx", SECRETO_RFC);

        assertTrue(uri.startsWith("otpauth://totp/GestoPago:ana%2Bpago%40correo.mx?"));
        assertTrue(uri.contains("secret=" + SECRETO_RFC));
        assertTrue(uri.contains("issuer=GestoPago"));
        assertTrue(uri.contains("digits=6") && uri.contains("period=30") && uri.contains("algorithm=SHA1"));
        assertFalse(uri.contains(" "));
    }

    @Test
    void sinRelojRegistradoUsaElDelSistema() {
        TotpService totp = new TotpService(new DefaultListableBeanFactory().getBeanProvider(Clock.class));

        assertTrue(Math.abs(totp.pasoActual() - Instant.now().getEpochSecond() / 30) <= 1);
    }

    @Test
    void siLaPlataformaNoOfreceHmacSeFallaSinRevelarDetalles() {
        try (MockedStatic<Mac> mac = Mockito.mockStatic(Mac.class)) {
            mac.when(() -> Mac.getInstance("HmacSHA1")).thenThrow(new NoSuchAlgorithmException("sin HMAC"));

            IllegalStateException error = assertThrows(IllegalStateException.class, () -> en(0).codigo(SECRETO_RFC, 1));
            assertEquals("No se pudo calcular el código TOTP", error.getMessage());
        }
    }
}
