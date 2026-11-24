package com.proyecto.servicios.service.exception;

public class PasswordInvalidaException extends ValidacionException {
    public PasswordInvalidaException() {
        super("Contraseña inválida: de 8 a 12 caracteres, con mayúscula, minúscula, número y carácter especial");
    }
}
