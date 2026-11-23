package com.proyecto.servicios.models.request;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

/**
 * Petición del cliente para pagar un servicio. El cliente no indica si el pago es con teléfono o
 * con referencia: la API lo decide con el tipoFront del producto en el catálogo.
 */
public record PagoServicioRequest(
        @NotBlank @Pattern(regexp = "[0-9]{18}", message = "debe contener 18 dígitos") String numeroCuenta,
        @NotNull @Positive Integer idProducto,
        @NotNull @Positive Integer idServicio,
        @NotNull @DecimalMin(value = "0.01", message = "debe ser mayor a cero")
        @Digits(integer = 16, fraction = 2) BigDecimal monto,
        @Pattern(regexp = "[0-9]{10}", message = "debe contener exactamente 10 dígitos numéricos") String telefono,
        @Pattern(regexp = "[A-Za-z0-9]{1,100}", message = "debe ser alfanumérica de 1 a 100 caracteres")
        String referencia,
        @NotBlank @Pattern(regexp = "[0-9]{4,6}", message = "debe contener de 4 a 6 dígitos") String pinAutorizacion) {

    @JsonAnySetter
    public void rechazarCampo(String nombre, Object valor) {
        throw new IllegalArgumentException("Campo de pago no permitido: " + nombre);
    }

    /** Evita que el PIN termine en los logs. */
    @Override
    public String toString() {
        return "PagoServicioRequest[numeroCuenta=" + numeroCuenta + ", idProducto=" + idProducto
                + ", idServicio=" + idServicio + ", monto=" + monto + "]";
    }
}
