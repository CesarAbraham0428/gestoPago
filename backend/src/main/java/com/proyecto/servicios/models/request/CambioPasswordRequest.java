package com.proyecto.servicios.models.request;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
public record CambioPasswordRequest(@NotBlank @Size(max=72) String passwordActual,
        @NotBlank @com.proyecto.servicios.validation.PasswordSegura String passwordNueva) {

    @JsonAnySetter
    public void rechazarCampo(String nombre, Object valor) {
        throw new IllegalArgumentException("Campo de contraseña no permitido: " + nombre);
    }
}
