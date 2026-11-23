package com.proyecto.servicios.models.response;

import java.util.List;

/** Nombres de los campos (curp, rfc, telefonoMovil, correoElectronico) que ya pertenecen a otra cuenta. */
public record DisponibilidadResponse(List<String> duplicados) { }
