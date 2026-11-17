package com.proyecto.servicios.models.request;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Segundo paso del inicio de sesión. {@code cerrarSesionAnterior} confirma que el usuario acepta terminar
 * la sesión que ya tiene abierta en otro dispositivo.
 */
public record VerificacionCodigoRequest(
        @NotBlank @Pattern(regexp = "[A-Za-z0-9_-]{43}") String desafioId,
        @NotBlank @Pattern(regexp = "\\d{6}", message = "Debe tener 6 dígitos.") String codigo,
        boolean cerrarSesionAnterior) {

    @JsonAnySetter
    public void rechazarCampo(String nombre, Object valor) {
        throw new IllegalArgumentException("Campo de verificación no permitido");
    }

    @Override
    public String toString() {
        return "VerificacionCodigoRequest[cerrarSesionAnterior=" + cerrarSesionAnterior + "]";
    }
}
