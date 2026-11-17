package com.proyecto.servicios.models.response;
import java.time.Instant;
public record UsuarioResponse(Integer id, Integer clienteId, String correo, boolean activo,
    Instant fechaCreacion, Instant fechaActualizacion) {}
