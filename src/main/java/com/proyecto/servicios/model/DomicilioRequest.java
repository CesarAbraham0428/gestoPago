package com.proyecto.servicios.model;
import jakarta.validation.constraints.*;
public record DomicilioRequest(
    @NotBlank @Size(max=1000) String calle,
    @NotBlank @Size(max=50) String numeroExterior,
    @Size(max=50) @Pattern(regexp=".*\\S.*") String numeroInterior,
    @NotBlank @Size(max=1000) String colonia,
    @NotBlank @Size(max=250) String municipio,
    @NotBlank @Size(max=250) String estado,
    @NotBlank @Pattern(regexp="[0-9]{5}") String codigoPostal,
    @NotBlank @Size(max=100) String pais
) {}
