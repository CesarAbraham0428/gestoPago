package com.proyecto.servicios.validation;

import com.proyecto.servicios.service.exception.PasswordInvalidaException;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

public final class PasswordValidator {
    private static final Pattern MAYUSCULA = Pattern.compile("[A-Z]");
    private static final Pattern MINUSCULA = Pattern.compile("[a-z]");
    private static final Pattern NUMERO = Pattern.compile("[0-9]");
    private static final Pattern ESPECIAL = Pattern.compile("[^A-Za-z0-9\\s]");

    private PasswordValidator() {}

    public static void validar(String password) {
        if (password == null || password.length() < 8 || password.length() > 72
            || password.getBytes(StandardCharsets.UTF_8).length > 72
            || !MAYUSCULA.matcher(password).find()
            || !MINUSCULA.matcher(password).find()
            || !NUMERO.matcher(password).find()
            || !ESPECIAL.matcher(password).find()) {
            throw new PasswordInvalidaException();
        }
    }
}
