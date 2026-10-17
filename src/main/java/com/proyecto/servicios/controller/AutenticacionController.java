package com.proyecto.servicios.controller;
import com.proyecto.servicios.models.request.LoginRequest;
import com.proyecto.servicios.models.response.AuthResponse;
import com.proyecto.servicios.models.response.GenericResponse;
import com.proyecto.servicios.service.AutenticacionService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping(value = "/auth", produces = MediaType.APPLICATION_JSON_VALUE)

public class AutenticacionController {
    private final AutenticacionService service;
    public AutenticacionController(AutenticacionService service) { this.service=service; }

    @PostMapping("/login") public GenericResponse<AuthResponse> iniciarSesion(@Valid @RequestBody LoginRequest r) {
        return GenericResponse.exito("Sesión iniciada", service.iniciarSesion(r));
    }
}
