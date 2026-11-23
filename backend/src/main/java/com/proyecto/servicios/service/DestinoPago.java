package com.proyecto.servicios.service;

import java.math.BigDecimal;

/**
 * Datos del pago que dependen del tipo de producto en el catálogo.
 *
 * @param montoPago solo en pagos de servicio; es lo que se envía a GestoPago
 * @param comision  lo que se cobra al cliente además del monto (cero en recargas)
 */
public record DestinoPago(String telefono, String referencia, BigDecimal montoPago, BigDecimal comision) {
}
