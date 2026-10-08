package com.proyecto.servicios.model;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
public record CambioPasswordRequest(@NotBlank @Size(max=72) String passwordActual, @NotBlank @Size(max=72) String passwordNueva) {}
