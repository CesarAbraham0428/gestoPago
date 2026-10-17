package com.proyecto.servicios.models.request;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.proyecto.servicios.validation.FormatosCliente;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Pattern;

public record ClienteIdentificacionRequest(
        @Pattern(regexp = FormatosCliente.RFC, flags = Pattern.Flag.CASE_INSENSITIVE) String rfc,
        @Pattern(regexp = FormatosCliente.CURP, flags = Pattern.Flag.CASE_INSENSITIVE) String curp) {

    @AssertTrue(message = "debes proporcionar RFC o CURP")
    @JsonIgnore
    public boolean isIdentificacionPresente() {
        return rfc != null || curp != null;
    }

    @JsonAnySetter
    public void rechazarCampo(String nombre, Object valor) {
        throw new IllegalArgumentException("Campo de identificación no permitido: " + nombre);
    }
}
