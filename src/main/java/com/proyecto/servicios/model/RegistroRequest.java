package com.proyecto.servicios.model;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegistroRequest(
        @NotBlank @Size(min = 3, max = 80)
        @Pattern(regexp = "[a-zA-Z0-9._-]+", message = "Usa letras, números, punto, guion o guion bajo")
        String usuario,
        @NotBlank @Size(min = 8, max = 72) String password,
        @NotBlank @Size(max = 120) String nombre,
        @NotBlank @Size(max = 120) String apellidoPaterno,
        @NotBlank @Size(max = 120) String apellidoMaterno,
        @NotBlank @Email @Size(max = 254) String correo,
        @NotBlank @Size(min = 10, max = 30) @Pattern(regexp = "\\+?[0-9]{10,15}") String telefono
) {
    @AssertTrue(message = "La contraseña no debe exceder 72 bytes UTF-8")
    public boolean isPasswordLengthSupported() {
        return password == null
                || password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length <= 72;
    }
}
