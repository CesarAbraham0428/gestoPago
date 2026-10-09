package com.proyecto.servicios.controller;
import com.proyecto.servicios.model.*;
import com.proyecto.servicios.service.UsuarioService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController 
@RequestMapping("/usuarios")

public class UsuarioController {

    private final UsuarioService service;
    
    public UsuarioController(UsuarioService service) { this.service=service; }
    @GetMapping("/{id}") public UsuarioResponse obtener(@PathVariable Integer id,Authentication auth) {
        return service.obtener(id,Integer.valueOf(auth.getName()));
    }
    @PutMapping("/{id}/password") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cambiarPassword(@PathVariable Integer id,@Valid @RequestBody CambioPasswordRequest r,Authentication auth) {
        service.cambiarPassword(id,Integer.valueOf(auth.getName()),r);
    }
}
