package com.proyecto.servicios.service;

import com.proyecto.servicios.model.AuthResponse;
import com.proyecto.servicios.model.LoginRequest;
import com.proyecto.servicios.model.RegistroRequest;

public interface AutenticacionService {
    AuthResponse registrar(RegistroRequest request);

    AuthResponse iniciarSesion(LoginRequest request);
}
