package com.proyecto.servicios.models.response;

public record AuthResponse(String token, String tipo, long expiraEnMs, String usuario, String nombreCompleto) {}
