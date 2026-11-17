package com.proyecto.servicios.entity.sf;

public enum EstadoPago {
    /** Saldo retenido; todavía no hay respuesta de GestoPago. */
    PENDIENTE,
    /** GestoPago respondió CODIGO 01; el cargo es definitivo. */
    APROBADO,
    /** GestoPago rechazó el pago o no se envió; el saldo se devolvió. */
    RECHAZADO,
    /** No se sabe si GestoPago aplicó el pago (timeout, 5xx); el saldo sigue retenido hasta conciliar. */
    EN_REVISION
}
