package com.proyecto.servicios.models.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;

/**
 * Comprobante que recibe el cliente, con los mismos datos que devuelve sendTx. No expone el SALDO ni la
 * COMISION de GestoPago (son del distribuidor), sino el saldo disponible de la cuenta del cliente.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PagoServicioResponse(
        String estado,
        Long idTx,
        String numAutorizacion,
        BigDecimal monto,
        String referencia,
        BigDecimal saldoDisponible,
        String fecha,
        String pin,
        String legend) {
}
