package com.proyecto.servicios.models.request.gestopago;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import java.math.BigDecimal;

/**
 * Cuerpo que se serializa a JSON y se firma antes de enviarlo a sendTx.do (y, sin cambios, a confirmTx.do).
 * telefono siempre se envía: con tipoFront 2 va fijo en "1111111111" y en artículos canjeables
 * (idCatTipoServicio 10 y 11) en "#1111111111". referencia y montoPago solo van en pagos de servicio.
 *
 * @param horaLocal  yyyyMMddHHmmss, por ejemplo 20101109131526
 * @param upc        identificador único de la operación (3 a 15 caracteres, aleatorio)
 * @param unidad     nombre de la sucursal o unidad comercial (3 a 25 caracteres)
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({"idProducto", "idServicio", "telefono", "horaLocal", "referencia", "montoPago", "upc", "unidad"})
public record GestoPagoSendTxRequest(
        Integer idProducto,
        Integer idServicio,
        String telefono,
        String horaLocal,
        String referencia,
        BigDecimal montoPago,
        String upc,
        String unidad) {
}
