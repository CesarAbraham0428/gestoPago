package com.proyecto.servicios.models.response;

import com.proyecto.servicios.entity.sf.Notificacion;
import java.time.Instant;

/** Aviso de la bandeja del usuario. {@code upc} enlaza con el pago cuando la notificación nace de uno. */
public record NotificacionResponse(Integer id, String tipo, String titulo, String mensaje, String upc,
        boolean leida, Instant fecha) {

    public static NotificacionResponse desde(Notificacion n) {
        return new NotificacionResponse(n.getId(), n.getTipo().name(), n.getTitulo(), n.getMensaje(), n.getUpc(),
                n.isLeida(), n.getFecha());
    }
}
