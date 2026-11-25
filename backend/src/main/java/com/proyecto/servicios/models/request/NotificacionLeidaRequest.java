package com.proyecto.servicios.models.request;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/** Notificación del usuario autenticado que se marca como leída. */
public record NotificacionLeidaRequest(@NotNull @Positive Integer id) {

    @JsonAnySetter
    public void rechazarCampo(String nombre, Object valor) {
        throw new IllegalArgumentException("Campo de notificación no permitido: " + nombre);
    }
}
