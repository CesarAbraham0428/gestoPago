package com.proyecto.servicios.model;
import jakarta.validation.constraints.NotNull;
public record EstadoCuentaRequest(@NotNull Boolean activa) {}
