package com.proyecto.servicios.service;

import com.proyecto.servicios.models.request.PagoServicioRequest;
import com.proyecto.servicios.models.response.ConsultaPagoResponse;
import com.proyecto.servicios.models.response.PagoServicioResponse;

public interface PagoService {

    PagoServicioResponse pagar(PagoServicioRequest request, Integer usuarioAutenticadoId);

    /** Estado del pago según el registro local; no llama a GestoPago. 404 si no existe o es de otro usuario. */
    ConsultaPagoResponse consultar(String upc, Integer usuarioAutenticadoId);
}
