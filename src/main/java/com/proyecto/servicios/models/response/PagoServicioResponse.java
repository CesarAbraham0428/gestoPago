package com.proyecto.servicios.models.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;

/**
 * Comprobante que recibe el cliente. No expone el SALDO de GestoPago (es el saldo del distribuidor),
 * sino el saldo disponible de la cuenta del cliente después del cargo.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PagoServicioResponse(
        Integer folio,
        String estado,
        String numeroCuenta,
        String upc,
        Long idTx,
        String numAutorizacion,
        BigDecimal monto,
        BigDecimal comision,
        String telefono,
        String referencia,
        BigDecimal saldoDisponible,
        String fecha,
        String pin,
        String legend) {
}
