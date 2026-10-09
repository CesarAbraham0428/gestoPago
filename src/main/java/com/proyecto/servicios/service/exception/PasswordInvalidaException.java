package com.proyecto.servicios.service.exception;

public class PasswordInvalidaException extends ValidacionException {
    public PasswordInvalidaException() {
        super("Contraseña inválida: mínimo 8 caracteres, mayúscula, minúscula, número y carácter especial; máximo 72 bytes UTF-8");
    }
}
