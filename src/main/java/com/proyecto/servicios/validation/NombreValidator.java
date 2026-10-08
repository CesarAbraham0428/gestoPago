package com.proyecto.servicios.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.util.regex.Pattern;

public class NombreValidator implements ConstraintValidator<NombreValido, String> {
    private static final Pattern LETRAS = Pattern.compile("[A-Za-zÁÉÍÓÚÜÑáéíóúüñ ]+");

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null) return true;
        String nombre = value.trim();
        return nombre.length() >= 2 && nombre.length() <= 50 && value.length() <= 50
            && LETRAS.matcher(nombre).matches();
    }
}
