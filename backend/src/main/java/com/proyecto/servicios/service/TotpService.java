package com.proyecto.servicios.service;

import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.util.OptionalLong;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.apache.commons.codec.binary.Base32;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Contraseñas de un solo uso basadas en tiempo (TOTP, RFC 6238: HMAC-SHA1, 6 dígitos, 30 s), compatibles con
 * Google Authenticator, Microsoft Authenticator y Authy. Todo ocurre sin red: el servidor y la app comparten el
 * secreto y el reloj. Se aceptan los códigos del paso anterior y siguiente para tolerar desfases de reloj.
 */
@Service
public class TotpService {

    private static final String EMISOR = "GestoPago";
    private static final int PERIODO_SEGUNDOS = 30;
    private static final int DIGITOS = 6;
    private static final SecureRandom ALEATORIO = new SecureRandom();

    private final Clock clock;

    @Autowired
    public TotpService(ObjectProvider<Clock> clock) {
        this(clock.getIfAvailable(Clock::systemUTC));
    }

    TotpService(Clock clock) {
        this.clock = clock;
    }

    /** 20 bytes aleatorios en Base32: 32 caracteres sin relleno ("="), que algunas apps no toleran. */
    public String nuevoSecreto() {
        byte[] bytes = new byte[20];
        ALEATORIO.nextBytes(bytes);
        return new Base32().encodeAsString(bytes);
    }

    /** URI {@code otpauth://} que las apps leen del código QR. */
    public String uri(String correo, String secreto) {
        String etiqueta = codificar(EMISOR) + ":" + codificar(correo);
        return "otpauth://totp/" + etiqueta + "?secret=" + secreto + "&issuer=" + codificar(EMISOR)
                + "&algorithm=SHA1&digits=" + DIGITOS + "&period=" + PERIODO_SEGUNDOS;
    }

    /**
     * Paso de tiempo al que corresponde el código, si es válido y posterior a {@code ultimoPaso}. Exigir un paso
     * mayor al último usado impide reutilizar un código (RFC 6238, sección 5.2).
     */
    public OptionalLong verificar(String secreto, String codigo, long ultimoPaso) {
        if (secreto == null || codigo == null) {
            return OptionalLong.empty();
        }
        byte[] esperado = codigo.getBytes(StandardCharsets.UTF_8);
        long actual = pasoActual();
        long encontrado = -1;
        for (long paso = actual - 1; paso <= actual + 1; paso++) {
            // Se comparan los tres sin cortar al primero: el tiempo de respuesta no revela cuál coincidió.
            boolean coincide = MessageDigest.isEqual(codigo(secreto, paso).getBytes(StandardCharsets.UTF_8), esperado);
            if (coincide && paso > ultimoPaso) {
                encontrado = paso;
            }
        }
        return encontrado < 0 ? OptionalLong.empty() : OptionalLong.of(encontrado);
    }

    public long pasoActual() {
        return clock.instant().getEpochSecond() / PERIODO_SEGUNDOS;
    }

    /** Código de 6 dígitos del paso dado (HOTP, RFC 4226). */
    public String codigo(String secreto, long paso) {
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(new Base32().decode(secreto), "HmacSHA1"));
            byte[] hash = mac.doFinal(ByteBuffer.allocate(8).putLong(paso).array());
            int desplazamiento = hash[hash.length - 1] & 0x0F;
            int binario = ((hash[desplazamiento] & 0x7F) << 24) | ((hash[desplazamiento + 1] & 0xFF) << 16)
                    | ((hash[desplazamiento + 2] & 0xFF) << 8) | (hash[desplazamiento + 3] & 0xFF);
            return String.format("%0" + DIGITOS + "d", binario % 1_000_000);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("No se pudo calcular el código TOTP");
        }
    }

    private static String codificar(String texto) {
        return URLEncoder.encode(texto, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
