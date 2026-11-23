package com.proyecto.servicios.controller.advice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Set;
import org.junit.jupiter.api.Test;

class CombinacionDeMensajesTest {

    static class Caso {
        @Size(min = 5, message = "demasiado corto")
        @Pattern(regexp = "[0-9]+", message = "solo números")
        String distintos = "ab";

        @Size(min = 5, message = "inválido")
        @Pattern(regexp = ".{6,}", message = "inválido")
        String iguales = "ab";
    }

    @Test
    void variosErroresDeUnMismoCampoSeUnenYLosRepetidosSeColapsan() {
        Validator validador = Validation.buildDefaultValidatorFactory().getValidator();
        ConstraintViolationException excepcion = new ConstraintViolationException(validador.validate(new Caso()));

        String mensaje = new GlobalExceptionHandler().restricciones(excepcion).getBody().getMensaje();

        assertTrue(mensaje.contains("demasiado corto") && mensaje.contains("solo números"), mensaje);
        assertTrue(mensaje.contains("iguales: inválido"), mensaje);
        assertEquals(1, mensaje.split("iguales: inválido", -1).length - 1, mensaje);
        assertTrue(!mensaje.contains("inválido; inválido"), mensaje);
    }

    @Test
    void sinViolacionesElMensajeEsElGenerico() {
        String mensaje = new GlobalExceptionHandler()
                .restricciones(new ConstraintViolationException(Set.of())).getBody().getMensaje();

        assertEquals("Verifique la información capturada e intente nuevamente.", mensaje);
    }
}
