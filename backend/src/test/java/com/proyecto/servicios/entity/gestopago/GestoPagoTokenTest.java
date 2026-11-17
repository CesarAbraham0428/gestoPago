package com.proyecto.servicios.entity.gestopago;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class GestoPagoTokenTest {

    @Test
    void alCrearseRegistraLasFechasYAlActualizarseSoloLaDeActualizacion() {
        GestoPagoToken token = new GestoPagoToken();

        token.onCreate();
        LocalDateTime creacion = token.getFechaCreacion();
        assertNotNull(creacion);
        assertNotNull(token.getFechaActualizacion());
        assertTrue(token.getActivo());

        token.setFechaActualizacion(creacion.minusDays(1));
        token.onUpdate();

        assertTrue(token.getFechaActualizacion().isAfter(creacion.minusDays(1)));
        assertTrue(token.getFechaCreacion().equals(creacion));
    }
}
