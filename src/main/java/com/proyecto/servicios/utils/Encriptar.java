package com.proyecto.servicios.utils;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Base64;

/** AES-256/CBC/PKCS5Padding con llave (32 bytes) e IV (16 bytes) en Base64. Nunca registra la llave ni el IV. */
public final class Encriptar {

    private static final String TRANSFORMACION = "AES/CBC/PKCS5Padding";

    private Encriptar() {
    }

    public static String AESCipher(Mode mode, String input, String ivBase64, String llaveBase64) {
        SecretKey llave = llave(llaveBase64);
        IvParameterSpec iv = iv(ivBase64);
        return mode == Mode.ENCRYPT ? encrypt(input, llave, iv) : decrypt(input, llave, iv);
    }

    public static String encrypt(String input, SecretKey secretKey, IvParameterSpec ivspec) {
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMACION);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, ivspec);
            return Base64.getEncoder().encodeToString(cipher.doFinal(input.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("No se pudo cifrar la información", e);
        }
    }

    public static String decrypt(String input, SecretKey secretKey, IvParameterSpec ivspec) {
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMACION);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, ivspec);
            return new String(cipher.doFinal(Base64.getDecoder().decode(input.trim())), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new IllegalStateException("No se pudo descifrar la información", e);
        }
    }

    private static SecretKey llave(String llaveBase64) {
        byte[] llave = decodificar(llaveBase64, "llave");
        // GestoPago exige AES-256.
        if (llave.length != 32) {
            throw new IllegalStateException("La llave AES-256 debe medir 32 bytes");
        }
        return new SecretKeySpec(llave, "AES");
    }

    private static IvParameterSpec iv(String ivBase64) {
        byte[] iv = decodificar(ivBase64, "IV");
        if (iv.length != 16) {
            throw new IllegalStateException("El IV de AES debe medir 16 bytes");
        }
        return new IvParameterSpec(iv);
    }

    private static byte[] decodificar(String base64, String nombre) {
        if (base64 == null || base64.isBlank()) {
            throw new IllegalStateException("No se configuró el " + nombre + " de cifrado");
        }
        try {
            return Base64.getDecoder().decode(base64.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("El " + nombre + " de cifrado no está en Base64", e);
        }
    }

    public enum Mode {
        ENCRYPT, DECRYPT
    }
}
