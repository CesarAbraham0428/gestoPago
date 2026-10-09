package com.proyecto.servicios.model;
public record DomicilioResponse(Integer id, String calle, String numeroExterior, String numeroInterior,
    String colonia, String municipio, String estado, String codigoPostal, String pais) {}
