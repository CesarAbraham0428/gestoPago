package com.proyecto.servicios.controller;

import com.proyecto.servicios.models.request.PagoServicioRequest;
import com.proyecto.servicios.models.response.GenericResponse;
import com.proyecto.servicios.models.response.PagoServicioResponse;
import com.proyecto.servicios.service.LimitePagosService;
import com.proyecto.servicios.service.PagoService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
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
}
