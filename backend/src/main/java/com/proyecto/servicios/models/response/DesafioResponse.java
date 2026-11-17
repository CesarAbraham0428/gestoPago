package com.proyecto.servicios.models.response;

/** Respuesta del primer paso: el código fue enviado al correo y se debe verificar con {@code desafioId}. */
public record DesafioResponse(String desafioId, long expiraEnMs, String correoEnmascarado) {}
