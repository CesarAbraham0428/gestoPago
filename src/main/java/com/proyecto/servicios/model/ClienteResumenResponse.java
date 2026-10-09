package com.proyecto.servicios.model;

import java.time.Instant;

public record ClienteResumenResponse(Integer id, String primerNombre, String segundoNombre,
    String apellidoPaterno, String apellidoMaterno, boolean activo, Instant fechaCreacion) {}
