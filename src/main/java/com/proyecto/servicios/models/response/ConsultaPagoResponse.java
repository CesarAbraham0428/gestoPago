package com.proyecto.servicios.models.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;

/**
 * Estado de un pago según el registro local. No incluye el texto de GestoPago ni datos internos:
 * el mensaje es propio de la API.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ConsultaPagoResponse(
        String upc,
        String estado,
        String mensaje,
        BigDecimal monto,
        BigDecimal comision,
        String referencia,
        Long idTx,
        String numAutorizacion) {
}
