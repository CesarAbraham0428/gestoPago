package com.proyecto.servicios.models.request;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/** Página de la bandeja de notificaciones del usuario autenticado. Sin valores: primera página de 20. */
public record NotificacionesRequest(@Min(0) Integer pagina, @Min(1) @Max(100) Integer tamanio) {

    public NotificacionesRequest {
        pagina = pagina == null ? 0 : pagina;
        tamanio = tamanio == null ? 20 : tamanio;
    }

    @JsonAnySetter
    public void rechazarCampo(String nombre, Object valor) {
        throw new IllegalArgumentException("Campo de notificaciones no permitido: " + nombre);
    }
}
