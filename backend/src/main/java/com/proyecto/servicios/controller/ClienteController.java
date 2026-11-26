package com.proyecto.servicios.controller;

import com.proyecto.servicios.models.request.ClienteActualizacionPorIdentidadRequest;
import com.proyecto.servicios.models.request.ClienteConsultaRequest;
import com.proyecto.servicios.models.request.ClienteDisponibilidadRequest;
import com.proyecto.servicios.models.request.ClienteIdentificacionRequest;
import com.proyecto.servicios.models.request.ClienteRequest;
import com.proyecto.servicios.models.response.ClienteResponse;
import com.proyecto.servicios.models.response.DisponibilidadResponse;
import com.proyecto.servicios.models.response.ListaGenericResponse;
import com.proyecto.servicios.models.response.GenericResponse;
import com.proyecto.servicios.service.ClienteService;
import com.proyecto.servicios.service.LimiteAutenticacionService;
import com.proyecto.servicios.utils.DireccionCliente;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(value = "/clientes", produces = MediaType.APPLICATION_JSON_VALUE)

public class ClienteController {
    private final ClienteService service;
    private final LimiteAutenticacionService limite;

    public ClienteController(ClienteService service, LimiteAutenticacionService limite) {
        this.service = service;
        this.limite = limite;
    }

    @PostMapping
    public ResponseEntity<GenericResponse<ClienteResponse>> registrar(@Valid @RequestBody ClienteRequest request,
            HttpServletRequest http) {
        limite.verificarRegistro(DireccionCliente.de(http));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(GenericResponse.exito("Cliente registrado", service.registrar(request)));
    }

    /** Pública: permite avisar de un dato repetido en el paso del formulario donde se captura. */
    @PostMapping("/disponibilidad")
    public GenericResponse<DisponibilidadResponse> disponibilidad(
            @Valid @RequestBody ClienteDisponibilidadRequest request, HttpServletRequest http) {
        limite.verificarDisponibilidad(DireccionCliente.de(http));
        return GenericResponse.exito("Disponibilidad consultada",
                new DisponibilidadResponse(service.datosDuplicados(request)));
    }

    @PostMapping("/consultar")
    public GenericResponse<ListaGenericResponse<ClienteResponse>> consultar(
            @Valid @RequestBody ClienteConsultaRequest request, Authentication authentication) {
        return GenericResponse.exito("Consulta de clientes realizada",
                service.consultar(request, Integer.valueOf(authentication.getName())));
    }

    @PatchMapping
    public GenericResponse<ClienteResponse> actualizarParcial(
            @Valid @RequestBody ClienteActualizacionPorIdentidadRequest request,
            Authentication authentication) {
        return GenericResponse.exito("Cliente actualizado",
                service.actualizarParcial(request, Integer.valueOf(authentication.getName())));
    }

    @PostMapping("/desactivar")
    public GenericResponse<Void> desactivar(@Valid @RequestBody ClienteIdentificacionRequest request,
            Authentication authentication) {
        service.desactivar(request, Integer.valueOf(authentication.getName()));
        return GenericResponse.exito("Cliente desactivado", null);
    }

    @PostMapping("/activar")
    public GenericResponse<Void> activar(@Valid @RequestBody ClienteIdentificacionRequest request,
            Authentication authentication) {
        service.activar(request, Integer.valueOf(authentication.getName()));
        return GenericResponse.exito("Cliente activado", null);
    }
}
