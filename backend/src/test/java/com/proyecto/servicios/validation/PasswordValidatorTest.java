package com.proyecto.servicios.validation;

import com.proyecto.servicios.service.exception.PasswordInvalidaException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

class PasswordValidatorTest {
    @ParameterizedTest
    @NullSource
    @ValueSource(strings={"Ab1!", "segura123!", "SEGURA123!", "Seguraabc!", "Segura123"})
    void rechazaLaAusenciaDeCadaRequisito(String password) {
        assertThrows(PasswordInvalidaException.class, () -> PasswordValidator.validar(password));
    }
    @ParameterizedTest
    @ValueSource(strings={"Abcdef1!", "Segura123!"})
    void aceptaPasswordConTodosLosRequisitos(String password) {
        assertDoesNotThrow(() -> PasswordValidator.validar(password));
    }
    @Test void aceptaDe8A12CaracteresYRechazaUnoMas() {
        assertDoesNotThrow(() -> PasswordValidator.validar("Aa1!" + "a".repeat(4)));
        assertDoesNotThrow(() -> PasswordValidator.validar("Aa1!" + "a".repeat(8)));
        assertThrows(PasswordInvalidaException.class, () -> PasswordValidator.validar("Aa1!" + "a".repeat(9)));
        assertThrows(PasswordInvalidaException.class, () -> PasswordValidator.validar("Aa1!" + "a".repeat(3)));
    }
}
