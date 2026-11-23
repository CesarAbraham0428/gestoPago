package com.proyecto.servicios.models.request;

import com.proyecto.servicios.validation.FormatosCliente;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Datos únicos a comprobar antes de registrar; los que no se envían no se consultan. */
public record ClienteDisponibilidadRequest(
    @Pattern(regexp=FormatosCliente.CURP) String curp,
    @Pattern(regexp=FormatosCliente.RFC) String rfc,
    @Pattern(regexp="[0-9]{10}") String telefonoMovil,
    @Email @Size(max=100) String correoElectronico
) { }
