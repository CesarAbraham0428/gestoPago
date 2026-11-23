package com.proyecto.servicios.models.request;

import com.proyecto.servicios.entity.sf.EstadoCivil;
import com.proyecto.servicios.entity.sf.Sexo;
import java.math.BigDecimal;
import java.time.LocalDate;

/** Datos personales y de contacto que comparten el registro y la actualización de un cliente. */
public interface DatosCliente {
    String primerNombre();

    String segundoNombre();

    String apellidoPaterno();

    String apellidoMaterno();

    LocalDate fechaNacimiento();

    Sexo sexo();

    String nacionalidad();

    EstadoCivil estadoCivil();

    String correoElectronico();

    String telefonoMovil();

    String telefonoAlternativo();

    String ocupacion();

    String empresa();

    BigDecimal ingresoMensual();

    DomicilioRequest domicilio();
}
