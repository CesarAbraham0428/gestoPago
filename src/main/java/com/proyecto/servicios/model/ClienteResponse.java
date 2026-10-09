package com.proyecto.servicios.model;
import com.proyecto.servicios.entity.sf.*;
import java.time.*;
import java.math.BigDecimal;
import java.util.List;

public record ClienteResponse(Integer id, String primerNombre, String segundoNombre,
    String apellidoPaterno, String apellidoMaterno, LocalDate fechaNacimiento,
    String curp, String rfc, Sexo sexo, String nacionalidad, EstadoCivil estadoCivil,
    String correoElectronico, String telefonoMovil, String telefonoAlternativo,
    String ocupacion, String empresa, BigDecimal ingresoMensual, Rol rol, boolean activo,
    Instant fechaCreacion, Instant fechaActualizacion, DomicilioResponse domicilio,
    List<CuentaResponse> cuentas, UsuarioResponse usuario) {}
