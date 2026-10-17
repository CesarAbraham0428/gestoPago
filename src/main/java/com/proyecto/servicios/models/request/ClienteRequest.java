package com.proyecto.servicios.models.request;
import com.proyecto.servicios.entity.sf.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.proyecto.servicios.validation.FormatosCliente;

public record ClienteRequest(
    @NotBlank @com.proyecto.servicios.validation.NombreValido String primerNombre,
    @com.proyecto.servicios.validation.NombreValido String segundoNombre,
    @NotBlank @com.proyecto.servicios.validation.NombreValido String apellidoPaterno,
    @NotBlank @com.proyecto.servicios.validation.NombreValido String apellidoMaterno,
    @NotNull @Past LocalDate fechaNacimiento,
    @NotBlank @Pattern(regexp=FormatosCliente.CURP) String curp,
    @NotBlank @Pattern(regexp=FormatosCliente.RFC) String rfc,
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
    @NotBlank @com.proyecto.servicios.validation.PasswordSegura String password
) {
    @JsonAnySetter
    public void rechazarCampo(String nombre, Object valor) {
        throw new IllegalArgumentException("Campo de registro no permitido: " + nombre);
    }
}
