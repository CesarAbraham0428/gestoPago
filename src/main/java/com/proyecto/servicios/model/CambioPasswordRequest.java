package com.proyecto.servicios.model;
import jakarta.validation.constraints.NotBlank;
public record CambioPasswordRequest(@NotBlank String passwordActual, @NotBlank String passwordNueva) {}
