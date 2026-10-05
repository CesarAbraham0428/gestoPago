package com.proyecto.servicios.model;
import jakarta.validation.constraints.*;
public record LoginRequest(@NotBlank @Email @Size(max=100) String correo, @NotBlank String password) {}
