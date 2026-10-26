package com.proyecto.servicios.controller;

import com.proyecto.servicios.models.request.CuentaConsultaRequest;
import com.proyecto.servicios.models.request.CuentaEstadoRequest;
import com.proyecto.servicios.models.request.CuentaPinRequest;
import com.proyecto.servicios.models.response.CuentaResponse;
import com.proyecto.servicios.models.response.ListaGenericResponse;
import com.proyecto.servicios.models.response.GenericResponse;
import com.proyecto.servicios.service.CuentaService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(value = "/cuentas", produces = MediaType.APPLICATION_JSON_VALUE)
public class CuentaController {

    private final CuentaService service;

    public CuentaController(CuentaService service) {
        this.service = service;
    }

    @PostMapping("/consultar")
    public GenericResponse<ListaGenericResponse<CuentaResponse>> consultar(
            @Valid @RequestBody CuentaConsultaRequest request, Authentication authentication) {
        return GenericResponse.exito("Consulta de cuentas realizada",
                service.consultar(request, Integer.valueOf(authentication.getName())));
    }

    @PatchMapping("/estado")
    public GenericResponse<CuentaResponse> cambiarEstado(
            @Valid @RequestBody CuentaEstadoRequest request, Authentication authentication) {
        return GenericResponse.exito("Estado de cuenta actualizado",
                service.cambiarEstado(request.numeroCuenta(), request.activa(),
                        Integer.valueOf(authentication.getName())));
    }

    @PutMapping("/pin")
    public GenericResponse<Void> configurarPin(
            @Valid @RequestBody CuentaPinRequest request, Authentication authentication) {
        service.configurarPin(request.numeroCuenta(), request.pinActual(), request.pinNuevo(),
                Integer.valueOf(authentication.getName()));
        return GenericResponse.exito("PIN de autorización actualizado", null);
    }
}
