package com.proyecto.servicios.controller;

import com.proyecto.servicios.models.request.NotificacionLeidaRequest;
import com.proyecto.servicios.models.request.NotificacionesRequest;
import com.proyecto.servicios.models.response.GenericResponse;
import com.proyecto.servicios.models.response.NotificacionesResponse;
import com.proyecto.servicios.service.NotificacionService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(value = "/notificaciones", produces = MediaType.APPLICATION_JSON_VALUE)
public class NotificacionController {

    private final NotificacionService service;

    public NotificacionController(NotificacionService service) {
        this.service = service;
    }

    @PostMapping("/consultar")
    public GenericResponse<NotificacionesResponse> consultar(
            @Valid @RequestBody NotificacionesRequest request, Authentication authentication) {
        return GenericResponse.exito("Notificaciones consultadas",
                service.listar(Integer.valueOf(authentication.getName()), request.pagina(), request.tamanio()));
    }

    @PostMapping("/leer")
    public GenericResponse<Void> leer(
            @Valid @RequestBody NotificacionLeidaRequest request, Authentication authentication) {
        service.marcarLeida(Integer.valueOf(authentication.getName()), request.id());
        return GenericResponse.exito("Notificación marcada como leída", null);
    }

    @PostMapping("/leer-todas")
    public GenericResponse<Void> leerTodas(Authentication authentication) {
        service.marcarTodasLeidas(Integer.valueOf(authentication.getName()));
        return GenericResponse.exito("Notificaciones marcadas como leídas", null);
    }
}
