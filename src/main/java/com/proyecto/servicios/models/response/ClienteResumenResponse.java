package com.proyecto.servicios.models.response;

import java.time.Instant;

public record ClienteResumenResponse(Integer id, String primerNombre, String segundoNombre,
    String apellidoPaterno, String apellidoMaterno, boolean activo, Instant fechaCreacion) {}
