package com.proyecto.servicios.models.request;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.proyecto.servicios.validation.FormatosCliente;
import com.proyecto.servicios.validation.NombreValido;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class ClienteConsultaRequest {
    @Pattern(regexp = FormatosCliente.RFC, flags = Pattern.Flag.CASE_INSENSITIVE)
    private String rfc;
    @Pattern(regexp = FormatosCliente.CURP, flags = Pattern.Flag.CASE_INSENSITIVE)
    private String curp;
    @NombreValido
    private String nombre;
    @Email @Size(max = 100) @Pattern(regexp = ".*\\S.*", message = "no puede estar vacío")
    private String correo;
    @Pattern(regexp = "[0-9]{18}", message = "debe contener 18 dígitos")
    private String numeroCuenta;
    private Boolean activo;
    private LocalDate desde;
    private LocalDate hasta;
    @NotNull @Min(0)
    private Integer pagina = 0;
    @NotNull @Min(1) @Max(100)
    private Integer tamanio = 20;

    @AssertTrue(message = "desde no puede ser posterior a hasta")
    @JsonIgnore
    public boolean isRangoFechasValido() {
        return desde == null || hasta == null || !desde.isAfter(hasta);
    }

    @AssertTrue(message = "las fechas deben tener un año entre 1 y 9999")
    @JsonIgnore
    public boolean isFechasEnRango() {
        return fechaEnRango(desde) && fechaEnRango(hasta);
    }

    private boolean fechaEnRango(LocalDate fecha) {
        return fecha == null || (fecha.getYear() >= 1 && fecha.getYear() <= 9999);
    }

    @JsonAnySetter
    public void rechazarCampo(String nombre, Object valor) {
        throw new IllegalArgumentException("Filtro de cliente no permitido: " + nombre);
    }
}
