package com.proyecto.servicios.service;

import com.proyecto.servicios.entity.gestopago.GestoPagoToken;

import java.util.Optional;

public interface GestoPagoTokenService {

    /** Pide un token nuevo a GestoPago y lo persiste, aunque el actual siga vigente. */
    GestoPagoToken renovarToken();

    /** Renueva el token solo si no existe o está por vencer. */
    void renovarSiHaceFalta();

    Optional<GestoPagoToken> obtenerTokenActivo(Integer idDistribuidor, String codigoDispositivo);
}
