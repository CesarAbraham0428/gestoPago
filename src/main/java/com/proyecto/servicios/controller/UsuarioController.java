package com.proyecto.servicios.controller;

import com.proyecto.servicios.models.request.CambioPasswordRequest;
import com.proyecto.servicios.models.request.UsuarioConsultaRequest;
import com.proyecto.servicios.models.response.GenericResponse;
import com.proyecto.servicios.models.response.ListaGenericResponse;
import com.proyecto.servicios.models.response.UsuarioResponse;
import com.proyecto.servicios.service.UsuarioService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(value = "/usuarios", produces = MediaType.APPLICATION_JSON_VALUE)
public class UsuarioController {

    private final UsuarioService service;

    public UsuarioController(UsuarioService service) {
        this.service = service;
    }

    @GetMapping("/me")
    public GenericResponse<UsuarioResponse> obtener(Authentication authentication) {
        return GenericResponse.exito("Usuario consultado", service.obtener(Integer.valueOf(authentication.getName())));
    }

    @PostMapping("/consultar")
    public GenericResponse<ListaGenericResponse<UsuarioResponse>> consultar(
            @Valid @RequestBody UsuarioConsultaRequest request, Authentication authentication) {
        return GenericResponse.exito("Consulta de usuarios realizada",
                service.consultar(request, Integer.valueOf(authentication.getName())));
    }

    @PutMapping("/me/password")
    public GenericResponse<Void> cambiarPassword(@Valid @RequestBody CambioPasswordRequest request,
            Authentication authentication) {
        service.cambiarPassword(Integer.valueOf(authentication.getName()), request);
        return GenericResponse.exito("Contraseña actualizada", null);
    }
}
