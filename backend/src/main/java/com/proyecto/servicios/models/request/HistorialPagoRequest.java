package com.proyecto.servicios.models.request;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/** Página del historial de pagos del usuario autenticado. Sin valores: primera página de 20. */
public record HistorialPagoRequest(@Min(0) Integer pagina, @Min(1) @Max(100) Integer tamanio) {

    public HistorialPagoRequest {
        pagina = pagina == null ? 0 : pagina;
        tamanio = tamanio == null ? 20 : tamanio;
    }

    @JsonAnySetter
    public void rechazarCampo(String nombre, Object valor) {
        throw new IllegalArgumentException("Campo de historial no permitido: " + nombre);
    }
}
