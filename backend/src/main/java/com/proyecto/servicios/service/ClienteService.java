package com.proyecto.servicios.service;

import com.proyecto.servicios.models.request.ClienteActualizacionPorIdentidadRequest;
import com.proyecto.servicios.models.request.ClienteConsultaRequest;
import com.proyecto.servicios.models.request.ClienteDisponibilidadRequest;
import com.proyecto.servicios.models.request.ClienteIdentificacionRequest;
import com.proyecto.servicios.models.request.ClienteRequest;
import com.proyecto.servicios.models.response.ClienteResponse;
import com.proyecto.servicios.models.response.ListaGenericResponse;
import java.util.List;

public interface ClienteService {
    /** Campos de la solicitud que ya pertenecen a otra cuenta (vacío si todos están libres). */
    List<String> datosDuplicados(ClienteDisponibilidadRequest request);

    ClienteResponse registrar(ClienteRequest request);

    ListaGenericResponse<ClienteResponse> consultar(ClienteConsultaRequest request,
            Integer usuarioAutenticadoId);

    ClienteResponse actualizarParcial(ClienteActualizacionPorIdentidadRequest request,
            Integer usuarioAutenticadoId);

    void desactivar(ClienteIdentificacionRequest request, Integer usuarioAutenticadoId);
}
