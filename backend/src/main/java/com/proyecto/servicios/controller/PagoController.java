package com.proyecto.servicios.controller;

import com.proyecto.servicios.models.request.ConsultaPagoRequest;
import com.proyecto.servicios.models.request.PagoServicioRequest;
import com.proyecto.servicios.models.response.ConsultaPagoResponse;
import com.proyecto.servicios.models.response.GenericResponse;
import com.proyecto.servicios.models.response.HistorialPagoResponse;
import com.proyecto.servicios.models.response.ListaGenericResponse;
import com.proyecto.servicios.models.response.PagoServicioResponse;
import com.proyecto.servicios.service.LimitePagosService;
import com.proyecto.servicios.service.PagoService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(value = "/pagos", produces = MediaType.APPLICATION_JSON_VALUE)
public class PagoController {

    private final PagoService service;
    private final LimitePagosService limite;

    public PagoController(PagoService service, LimitePagosService limite) {
        this.service = service;
        this.limite = limite;
    }

    @PostMapping
    public GenericResponse<PagoServicioResponse> pagar(
            @Valid @RequestBody PagoServicioRequest request, Authentication authentication) {
        Integer usuarioId = Integer.valueOf(authentication.getName());
        limite.verificar(usuarioId);
        return GenericResponse.exito("Pago realizado correctamente", service.pagar(request, usuarioId));
    }

    @GetMapping
    public GenericResponse<ListaGenericResponse<HistorialPagoResponse>> historial(
            @RequestParam(defaultValue = "0") int pagina, @RequestParam(defaultValue = "20") int tamanio,
            Authentication authentication) {
        Integer usuarioId = Integer.valueOf(authentication.getName());
        return GenericResponse.exito("Historial consultado",
                service.historial(usuarioId, Math.max(pagina, 0), Math.min(Math.max(tamanio, 1), 100)));
    }

    @PostMapping("/consulta")
    public GenericResponse<ConsultaPagoResponse> consultar(
            @Valid @RequestBody ConsultaPagoRequest request, Authentication authentication) {
        Integer usuarioId = Integer.valueOf(authentication.getName());
        return GenericResponse.exito("Consulta realizada correctamente", service.consultar(request.upc(), usuarioId));
    }
}
