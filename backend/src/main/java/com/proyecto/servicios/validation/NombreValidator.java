package com.proyecto.servicios.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.util.regex.Pattern;

public class NombreValidator implements ConstraintValidator<NombreValido, String> {
    private static final Pattern LETRAS = Pattern.compile("[A-Za-zÁÉÍÓÚÜÑáéíóúüñ ]+");
    private int min = 2;

    @Override
    public void initialize(NombreValido anotacion) {
        min = anotacion.min();
    }

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null) return true;
        String nombre = value.trim();
        return nombre.length() >= min && nombre.length() <= 50 && value.length() <= 50
            && LETRAS.matcher(nombre).matches();
    }
}
