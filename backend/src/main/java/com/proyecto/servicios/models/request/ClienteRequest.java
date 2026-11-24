package com.proyecto.servicios.models.request;
import com.proyecto.servicios.entity.sf.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.proyecto.servicios.validation.FormatosCliente;

public record ClienteRequest(
    @NotBlank @com.proyecto.servicios.validation.NombreValido(min=3) String primerNombre,
    @com.proyecto.servicios.validation.NombreValido(min=3) String segundoNombre,
    @NotBlank @com.proyecto.servicios.validation.NombreValido String apellidoPaterno,
    @NotBlank @com.proyecto.servicios.validation.NombreValido String apellidoMaterno,
    @NotNull @Past LocalDate fechaNacimiento,
    @NotBlank @Pattern(regexp=FormatosCliente.CURP) String curp,
    @NotBlank @Pattern(regexp=FormatosCliente.RFC) String rfc,
    @NotNull Sexo sexo,
    @NotBlank @Size(max=100) String nacionalidad,
    EstadoCivil estadoCivil,
    @NotBlank @Email @Size(max=100) String correoElectronico,
    @NotBlank @Pattern(regexp="[0-9]{10}") String telefonoMovil,
    @Pattern(regexp="[0-9]{10}") String telefonoAlternativo,
    @Size(max=50) @Pattern(regexp=FormatosCliente.CON_TEXTO) String ocupacion,
    @Size(max=50) @Pattern(regexp=FormatosCliente.CON_TEXTO) String empresa,
    @DecimalMin(value="0",inclusive=false) @Digits(integer=16,fraction=2) BigDecimal ingresoMensual,
    @Valid DomicilioRequest domicilio,
    @NotBlank @com.proyecto.servicios.validation.PasswordSegura String password
) implements DatosCliente {
    @JsonAnySetter
    public void rechazarCampo(String nombre, Object valor) {
        throw new IllegalArgumentException("Campo de registro no permitido: " + nombre);
    }
}
