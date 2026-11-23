package com.proyecto.servicios.controller;

import com.proyecto.servicios.models.request.LoginRequest;
import com.proyecto.servicios.models.request.VerificacionCodigoRequest;
import com.proyecto.servicios.models.response.AuthResponse;
import com.proyecto.servicios.models.response.DesafioResponse;
import com.proyecto.servicios.models.response.GenericResponse;
import com.proyecto.servicios.service.AutenticacionService;
import com.proyecto.servicios.service.LimiteAutenticacionService;
import com.proyecto.servicios.utils.DireccionCliente;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(value = "/auth", produces = MediaType.APPLICATION_JSON_VALUE)
public class AutenticacionController {

    private final AutenticacionService service;
    private final LimiteAutenticacionService limite;

    public AutenticacionController(AutenticacionService service, LimiteAutenticacionService limite) {
        this.service = service;
        this.limite = limite;
    }

    /** Paso 1: credenciales. Responde con un desafío (y los datos para enlazar la app la primera vez); el token se entrega hasta verificar el código. */
    @PostMapping("/login")
    public GenericResponse<DesafioResponse> iniciarSesion(@Valid @RequestBody LoginRequest request,
            HttpServletRequest http) {
        limite.verificarInicioDeSesion(DireccionCliente.de(http), request.correo());
        return GenericResponse.exito("Ingresa el código de tu app autenticadora", service.iniciarSesion(request));
    }

    /** Paso 2: código del segundo factor. 409 si ya hay otra sesión y no se confirmó cerrarla. */
    @PostMapping("/verificar")
    public GenericResponse<AuthResponse> verificar(@Valid @RequestBody VerificacionCodigoRequest request,
            HttpServletRequest http) {
        limite.verificarInicioDeSesion(DireccionCliente.de(http), null);
        return GenericResponse.exito("Sesión iniciada", service.verificarCodigo(request));
    }

    @PostMapping("/logout")
    public GenericResponse<Void> cerrarSesion(Authentication authentication) {
        service.cerrarSesion(Integer.valueOf(authentication.getName()));
        return GenericResponse.exito("Sesión cerrada", null);
    }

    /** Sonda de la app: el filtro JWT ya respondió 401 si la sesión se cerró o la reemplazó otro dispositivo. */
    @GetMapping("/sesion")
    public GenericResponse<Void> verificarSesion() {
        return GenericResponse.exito("Sesión activa", null);
    }
}
