package com.proyecto.servicios.service;
import com.proyecto.servicios.models.request.LoginRequest;
import com.proyecto.servicios.models.response.AuthResponse;
public interface AutenticacionService { AuthResponse iniciarSesion(LoginRequest request); }
