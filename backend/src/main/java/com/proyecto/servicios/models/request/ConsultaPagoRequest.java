package com.proyecto.servicios.models.request;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** Consulta del estado de un pago por su UPC (la referencia que recibió el cliente al pagar). */
public record ConsultaPagoRequest(
        @NotBlank @Pattern(regexp = "[A-Z0-9]{15}", message = "debe contener 15 caracteres alfanuméricos en mayúsculas")
        String upc) {

    @JsonAnySetter
    public void rechazarCampo(String nombre, Object valor) {
        throw new IllegalArgumentException("Campo de consulta no permitido: " + nombre);
    }
}
