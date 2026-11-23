package com.proyecto.servicios.models.request;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import jakarta.validation.constraints.*;
public record LoginRequest(@NotBlank @Email @Size(max=100) String correo, @NotBlank @Size(max=72) String password) {
    @JsonAnySetter
    public void rechazarCampo(String nombre, Object valor) {
        throw new IllegalArgumentException("Campo de autenticación no permitido");
    }

    /** La contraseña nunca debe terminar en un log. */
    @Override
    public String toString() {
        return "LoginRequest[correo=" + correo + "]";
    }
}
