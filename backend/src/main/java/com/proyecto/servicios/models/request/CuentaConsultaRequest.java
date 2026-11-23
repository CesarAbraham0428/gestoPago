package com.proyecto.servicios.models.request;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import java.math.BigDecimal;

public record CuentaConsultaRequest(
        Boolean activo,
        @DecimalMin(value = "0.00", message = "El saldo debe ser mayor o igual a cero.") @Digits(integer = 16, fraction = 2) BigDecimal saldo,
        @Pattern(regexp = "[0-9]{18}", message = "debe contener 18 dígitos") String numeroCuenta,
        @Min(0) Integer pagina,
        @Min(1) @Max(100) Integer tamanio) {

    public CuentaConsultaRequest {
        pagina = pagina == null ? 0 : pagina;
        tamanio = tamanio == null ? 20 : tamanio;
    }

    @JsonAnySetter
    public void rechazarCampo(String nombre, Object valor) {
        throw new IllegalArgumentException("Filtro de cuenta no permitido: " + nombre);
    }
}
