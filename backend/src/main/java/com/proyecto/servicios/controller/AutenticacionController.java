package com.proyecto.servicios.controller;

import com.proyecto.servicios.models.request.LoginRequest;
import com.proyecto.servicios.models.request.VerificacionCodigoRequest;
import com.proyecto.servicios.models.response.AuthResponse;
import com.proyecto.servicios.models.response.DesafioResponse;
import com.proyecto.servicios.models.response.GenericResponse;
import com.proyecto.servicios.service.AutenticacionService;
import com.proyecto.servicios.service.LimiteAutenticacionService;
import com.proyecto.servicios.service.NotificadorSesiones;
import com.proyecto.servicios.utils.DireccionCliente;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.util.function.Supplier;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping(value = "/auth", produces = MediaType.APPLICATION_JSON_VALUE)
public class AutenticacionController {

    private final AutenticacionService service;
    private final LimiteAutenticacionService limite;
    private final NotificadorSesiones notificador;

    public AutenticacionController(AutenticacionService service, LimiteAutenticacionService limite,
            NotificadorSesiones notificador) {
        this.service = service;
        this.limite = limite;
        this.notificador = notificador;
    }

    /** Paso 1: credenciales. Responde con un desafío (y los datos para enlazar la app la primera vez); el token se entrega hasta verificar el código. */
    @PostMapping("/login")
    public GenericResponse<DesafioResponse> iniciarSesion(@Valid @RequestBody LoginRequest request,
            HttpServletRequest http) {
        return GenericResponse.exito("Ingresa el código de tu app autenticadora",
                limitado(DireccionCliente.de(http), request.correo(), () -> service.iniciarSesion(request)));
    }

    /** Paso 2: código del segundo factor. 409 si ya hay otra sesión y no se confirmó cerrarla. */
    @PostMapping("/verificar")
    public GenericResponse<AuthResponse> verificar(@Valid @RequestBody VerificacionCodigoRequest request,
            HttpServletRequest http) {
        return GenericResponse.exito("Sesión iniciada",
                limitado(DireccionCliente.de(http), null, () -> service.verificarCodigo(request)));
    }

    /** Bloquea si ya hubo demasiados fallos y, si este intento falla por credenciales, lo anota. */
    private <T> T limitado(String ip, String correo, Supplier<T> intento) {
        limite.verificarInicioDeSesion(ip, correo);
        try {
            return intento.get();
        } catch (RuntimeException e) {
            limite.registrarFalloDeLogin(ip, correo, e);
            throw e;
        }
    }

    @PostMapping("/logout")
    public GenericResponse<Void> cerrarSesion(Authentication authentication) {
        service.cerrarSesion(Integer.valueOf(authentication.getName()));
        return GenericResponse.exito("Sesión cerrada", null);
    }

    /**
     * Canal Server-Sent Events de la sesión: emite {@code sesion_cerrada} con el motivo cuando la sesión se cierra
     * o la reemplaza otro dispositivo. Si la sesión no era válida al conectar, el filtro JWT ya respondió 401.
     */
    @GetMapping(value = "/sesion/eventos", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter eventosDeSesion(Authentication authentication, HttpServletResponse response) {
        response.setHeader("X-Accel-Buffering", "no"); // que ningún proxy retenga los eventos
        return notificador.suscribir(Integer.valueOf(authentication.getName()),
                (String) authentication.getDetails());
    }
}
