package com.proyecto.servicios.service;

import com.proyecto.servicios.models.request.PagoServicioRequest;
import com.proyecto.servicios.models.response.PagoServicioResponse;

public interface PagoService {

    PagoServicioResponse pagar(PagoServicioRequest request, Integer usuarioAutenticadoId);

    /** Consulta con confirmTx los pagos sin resultado (EN_REVISION o PENDIENTE abandonados). */
    void conciliarPendientes();
}
