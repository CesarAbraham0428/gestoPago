package com.proyecto.servicios.models.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * Pago del usuario autenticado en cualquier estado (idTx y numAutorizacion solo si fue aprobado). No expone el texto ni la respuesta de GestoPago.
 * producto y servicio son nulos si el catálogo ya no los contiene; total es monto + comision.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record HistorialPagoResponse(
        String upc,
        String estado,
        String producto,
        String servicio,
        Integer idProducto,
        Integer idServicio,
        BigDecimal monto,
        BigDecimal comision,
        BigDecimal total,
        String referencia,
        String telefono,
        Long idTx,
        String numAutorizacion,
        Instant fecha) {
}
