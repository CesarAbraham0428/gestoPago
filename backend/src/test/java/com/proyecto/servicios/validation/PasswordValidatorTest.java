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
    @Test void aceptaExactamente72BytesYRechazaUnByteMas() {
        assertDoesNotThrow(() -> PasswordValidator.validar("Aa1!" + "a".repeat(68)));
        assertThrows(PasswordInvalidaException.class, () -> PasswordValidator.validar("Aa1!" + "a".repeat(69)));
    }
    @Test void limitaBytesUtf8AunqueLaCantidadDeCaracteresSeaMenor() {
        assertDoesNotThrow(() -> PasswordValidator.validar("Aa1!" + "ñ".repeat(34)));
        assertThrows(PasswordInvalidaException.class, () -> PasswordValidator.validar("Aa1!" + "ñ".repeat(35)));
    }
}
