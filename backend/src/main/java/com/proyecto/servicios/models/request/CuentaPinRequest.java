package com.proyecto.servicios.models.request;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** pinActual solo es obligatorio cuando la cuenta ya tiene un PIN configurado. */
public record CuentaPinRequest(
        @NotBlank @Pattern(regexp = "[0-9]{18}", message = "debe contener 18 dígitos") String numeroCuenta,
        @Pattern(regexp = "[0-9]{4,6}", message = "debe contener de 4 a 6 dígitos") String pinActual,
        @NotBlank @Pattern(regexp = "[0-9]{4,6}", message = "debe contener de 4 a 6 dígitos") String pinNuevo) {

    @JsonAnySetter
    public void rechazarCampo(String nombre, Object valor) {
        throw new IllegalArgumentException("Campo de PIN no permitido: " + nombre);
    }

    @Override
    public String toString() {
        return "CuentaPinRequest[numeroCuenta=" + numeroCuenta + "]";
    }
}
