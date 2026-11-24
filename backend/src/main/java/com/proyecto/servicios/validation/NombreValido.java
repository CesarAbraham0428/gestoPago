package com.proyecto.servicios.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.*;

@Documented
@Constraint(validatedBy = NombreValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface NombreValido {
    String message() default "Debe contener solo letras y espacios, con la longitud mínima indicada y máximo 50 caracteres, sin espacios exteriores";
    /** Longitud mínima (sin espacios exteriores). */
    int min() default 2;
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
