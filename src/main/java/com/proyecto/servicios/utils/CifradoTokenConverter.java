package com.proyecto.servicios.utils;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Cifra el token Bearer de GestoPago en la BD con AES-256-GCM. Se guarda en Base64 como IV (12 bytes) + texto cifrado.
 * La llave (32 bytes en Base64) llega por variable de entorno y es distinta de la llave de firma de GestoPago.
 */
@Component
@Converter
public class CifradoTokenConverter implements AttributeConverter<String, String> {

    private static final String ALGORITMO = "AES/GCM/NoPadding";
    private static final int LONGITUD_IV = 12;
    private static final int LONGITUD_TAG_BITS = 128;

    private final SecretKeySpec llave;
    private final SecureRandom aleatorio = new SecureRandom();

    public CifradoTokenConverter(@Value("${gestopago.auth.token-key}") String llaveBase64) {
        byte[] bytes = Base64.getDecoder().decode(llaveBase64);
        if (bytes.length != 32) {
            throw new IllegalStateException("gestopago.auth.token-key debe ser una llave AES-256 (32 bytes en Base64)");
        }
        this.llave = new SecretKeySpec(bytes, "AES");
    }

    @Override
    public String convertToDatabaseColumn(String token) {
        if (token == null) {
            return null;
        }
        byte[] iv = new byte[LONGITUD_IV];
        aleatorio.nextBytes(iv);
        byte[] cifrado = ejecutar(Cipher.ENCRYPT_MODE, iv, token.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(ByteBuffer.allocate(iv.length + cifrado.length)
                .put(iv).put(cifrado).array());
    }

    @Override
    public String convertToEntityAttribute(String columna) {
        if (columna == null) {
            return null;
        }
        ByteBuffer datos = ByteBuffer.wrap(Base64.getDecoder().decode(columna));
        byte[] iv = new byte[LONGITUD_IV];
        datos.get(iv);
        byte[] cifrado = new byte[datos.remaining()];
        datos.get(cifrado);
        return new String(ejecutar(Cipher.DECRYPT_MODE, iv, cifrado), StandardCharsets.UTF_8);
    }

    private byte[] ejecutar(int modo, byte[] iv, byte[] datos) {
        try {
            Cipher cipher = Cipher.getInstance(ALGORITMO);
            cipher.init(modo, llave, new GCMParameterSpec(LONGITUD_TAG_BITS, iv));
            return cipher.doFinal(datos);
        } catch (GeneralSecurityException e) {
            // Sin causa: el mensaje del proveedor criptográfico no debe llegar a los logs.
            throw new IllegalStateException("No se pudo procesar el token cifrado de GestoPago");
        }
    }
}
