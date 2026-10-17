package com.proyecto.servicios.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class PasswordSeguraValidator implements ConstraintValidator<PasswordSegura, String> {
    @Override
    public boolean isValid(String valor, ConstraintValidatorContext context) {
        return valor == null || PasswordValidator.esValida(valor);
    }
}
