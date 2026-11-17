package com.proyecto.servicios.models.request;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;

public record ClienteActualizacionPorIdentidadRequest(
        @NotNull @Valid ClienteIdentificacionRequest identificacion,
        @NotNull JsonNode cambios) {

    @AssertTrue(message = "cambios debe ser un objeto JSON con al menos un campo")
    @JsonIgnore
    public boolean isCambiosValidos() {
        return cambios == null || (cambios.isObject() && !cambios.isEmpty());
    }

    @JsonAnySetter
    public void rechazarCampo(String nombre, Object valor) {
        throw new IllegalArgumentException("Campo de actualización no permitido: " + nombre);
    }
}
