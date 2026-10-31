package com.proyecto.servicios.models.request.gestopago;

/**
 * Cuerpo firmado para verifyReference.do. idProducto es el producto de consulta (tipoFront 4) del servicio,
 * no el producto que se paga.
 */
public record GestoPagoVerificarReferenciaRequest(Integer idProducto, Integer idServicio, String referencia) {
}
