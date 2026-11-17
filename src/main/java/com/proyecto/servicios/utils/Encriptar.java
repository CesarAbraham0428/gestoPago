package com.proyecto.servicios.utils;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Base64;

/**
 * AES-256/CBC/PKCS5Padding con llave (32 bytes) e IV (16 bytes) en Base64. Nunca registra la llave ni el IV.
 *
 * <p>Es el protocolo que impone GestoPago (llave e IV fijos entregados por el proveedor, con una firma de
 * referencia publicada en su especificación), por eso no se usa GCM ni un IV aleatorio. Los datos propios se
 * cifran con {@link CifradoTokenConverter} (AES-GCM con IV aleatorio).
 */
@SuppressWarnings({"java:S5542", "java:S6432", "java:S3329"}) // protocolo fijo del proveedor, ver arriba
final class Encriptar {

    private static final String TRANSFORMACION = "AES/CBC/PKCS5Padding";

    private Encriptar() {
    }

    static String cifrar(String input, String ivBase64, String llaveBase64) {
        byte[] cifrado = transformar(Cipher.ENCRYPT_MODE, input.getBytes(StandardCharsets.UTF_8), ivBase64,
                llaveBase64);
        return Base64.getEncoder().encodeToString(cifrado);
    }

    static String descifrar(String input, String ivBase64, String llaveBase64) {
        byte[] cifrado;
        try {
            cifrado = Base64.getDecoder().decode(input.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("La información cifrada no está en Base64", e);
        }
        return new String(transformar(Cipher.DECRYPT_MODE, cifrado, ivBase64, llaveBase64), StandardCharsets.UTF_8);
    }

    private static byte[] transformar(int modo, byte[] datos, String ivBase64, String llaveBase64) {
        SecretKeySpec llave = new SecretKeySpec(decodificar(llaveBase64, "llave", 32), "AES");
        IvParameterSpec iv = new IvParameterSpec(decodificar(ivBase64, "IV", 16));
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMACION);
            cipher.init(modo, llave, iv);
            return cipher.doFinal(datos);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("No se pudo procesar la información cifrada", e);
        }
    }

    private static byte[] decodificar(String base64, String nombre, int bytesEsperados) {
        if (base64 == null || base64.isBlank()) {
            throw new IllegalStateException("No se configuró el " + nombre + " de cifrado");
        }
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(base64.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("El " + nombre + " de cifrado no está en Base64", e);
        }
        if (bytes.length != bytesEsperados) {
            throw new IllegalStateException("El " + nombre + " de cifrado debe medir " + bytesEsperados + " bytes");
        }
        return bytes;
    }
}
