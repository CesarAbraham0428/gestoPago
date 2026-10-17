package com.proyecto.servicios.models.request;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record CuentaEstadoRequest(
        @NotBlank @Pattern(regexp = "[0-9]{18}", message = "debe contener 18 dígitos") String numeroCuenta,
        @NotNull Boolean activa) {

    @JsonAnySetter
    public void rechazarCampo(String nombre, Object valor) {
        throw new IllegalArgumentException("Campo de cuenta no permitido: " + nombre);
    }
}
