package com.proyecto.servicios.service;

import com.proyecto.servicios.models.request.LoginRequest;
import com.proyecto.servicios.models.request.VerificacionCodigoRequest;
import com.proyecto.servicios.models.response.AuthResponse;
import com.proyecto.servicios.models.response.DesafioResponse;

public interface AutenticacionService {

    /** Paso 1: valida usuario y contraseña y envía el código del segundo factor. No emite token. */
    DesafioResponse iniciarSesion(LoginRequest request);

    /** Paso 2: valida el código en el servidor y abre la sesión única del usuario. */
    AuthResponse verificarCodigo(VerificacionCodigoRequest request);

    /** Termina la sesión del usuario autenticado; su token deja de ser válido de inmediato. */
    void cerrarSesion(Integer usuarioId);
}
