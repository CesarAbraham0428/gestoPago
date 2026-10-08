package com.proyecto.servicios.model;
import com.proyecto.servicios.entity.sf.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;

public record ClienteRequest(
    @NotBlank @com.proyecto.servicios.validation.NombreValido String primerNombre,
    @com.proyecto.servicios.validation.NombreValido String segundoNombre,
    @NotBlank @com.proyecto.servicios.validation.NombreValido String apellidoPaterno,
    @NotBlank @com.proyecto.servicios.validation.NombreValido String apellidoMaterno,
    @NotNull @Past LocalDate fechaNacimiento,
    @NotBlank @Pattern(regexp="[A-Z][AEIOUX][A-Z]{2}[0-9]{2}(0[1-9]|1[0-2])(0[1-9]|[12][0-9]|3[01])[HM](AS|BC|BS|CC|CL|CM|CS|CH|DF|DG|GT|GR|HG|JC|MC|MN|MS|NT|NL|OC|PL|QT|QR|SP|SL|SR|TC|TS|TL|VZ|YN|ZS|NE)[B-DF-HJ-NP-TV-Z]{3}[A-Z0-9][0-9]") String curp,
    @NotBlank @Pattern(regexp="[A-ZÑ&]{3,4}[0-9]{2}(0[1-9]|1[0-2])(0[1-9]|[12][0-9]|3[01])[A-Z0-9]{3}") String rfc,
    @NotNull Sexo sexo,
    @NotBlank @Size(max=100) String nacionalidad,
    @NotNull EstadoCivil estadoCivil,
    @NotBlank @Email @Size(max=100) String correoElectronico,
    @NotBlank @Pattern(regexp="[0-9]{10}") String telefonoMovil,
    @Pattern(regexp="[0-9]{10}") String telefonoAlternativo,
    @NotBlank @Size(max=250) String ocupacion,
    @NotBlank @Size(max=250) String empresa,
    @NotNull @DecimalMin(value="0",inclusive=false) @Digits(integer=16,fraction=2) BigDecimal ingresoMensual,
    @NotNull @Valid DomicilioRequest domicilio,
    @NotBlank @Size(max=72) String password
) {}
