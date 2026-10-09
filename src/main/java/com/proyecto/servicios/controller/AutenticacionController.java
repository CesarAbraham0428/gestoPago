package com.proyecto.servicios.controller;
import com.proyecto.servicios.model.*;
import com.proyecto.servicios.service.AutenticacionService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/auth")
public class AutenticacionController {
    private final AutenticacionService service;
    public AutenticacionController(AutenticacionService service) { this.service=service; }
    @PostMapping("/login") public AuthResponse iniciarSesion(@Valid @RequestBody LoginRequest r) {
        return service.iniciarSesion(r);
    }
}
