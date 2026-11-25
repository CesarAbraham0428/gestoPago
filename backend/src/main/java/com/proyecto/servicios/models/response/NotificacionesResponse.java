package com.proyecto.servicios.models.response;

/** Página de notificaciones junto con el total sin leer (para el indicador de la campana). */
public record NotificacionesResponse(ListaGenericResponse<NotificacionResponse> lista, long noLeidas) {
}
