package com.proyecto.servicios.model;

public record AuthResponse(String token, String tipo, long expiraEnMs, String usuario, String nombreCompleto) {}
