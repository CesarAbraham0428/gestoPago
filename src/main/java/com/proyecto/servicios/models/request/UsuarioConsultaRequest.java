package com.proyecto.servicios.models.request;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.proyecto.servicios.validation.NombreValido;
import com.proyecto.servicios.validation.FormatosCliente;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UsuarioConsultaRequest(
        @Pattern(regexp = FormatosCliente.RFC, flags = Pattern.Flag.CASE_INSENSITIVE) String rfc,
        @Pattern(regexp = FormatosCliente.CURP, flags = Pattern.Flag.CASE_INSENSITIVE) String curp,
        @Email @Size(max = 100) @Pattern(regexp = ".*\\S.*", message = "no puede estar vacío") String correo,
        @NombreValido String nombre,
        Boolean activo,
        @Min(0) Integer pagina,
        @Min(1) @Max(100) Integer tamanio) {

    public UsuarioConsultaRequest {
        pagina = pagina == null ? 0 : pagina;
        tamanio = tamanio == null ? 20 : tamanio;
    }

    @JsonAnySetter
    public void rechazarCampo(String nombre, Object valor) {
        throw new IllegalArgumentException("Filtro de usuario no permitido");
    }
}
