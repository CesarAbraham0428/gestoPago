package com.proyecto.servicios.validation;
import com.proyecto.servicios.service.exception.NegocioException;
import org.springframework.http.HttpStatus;
import java.nio.charset.StandardCharsets;
public final class PasswordValidator {
    private PasswordValidator() {}
    public static void validar(String password) {
        if (password == null || password.length() < 8
            || password.getBytes(StandardCharsets.UTF_8).length > 72
            || !password.matches("(?s).*[A-Z].*")
            || !password.matches("(?s).*[a-z].*")
            || !password.matches("(?s).*[0-9].*")
            || !password.matches("(?s).*[^A-Za-z0-9\\s].*")) {
            throw new NegocioException(HttpStatus.BAD_REQUEST,
                "Contraseña inválida: mínimo 8 caracteres, mayúscula, minúscula, número y carácter especial; máximo 72 bytes UTF-8");
        }
    }
}
