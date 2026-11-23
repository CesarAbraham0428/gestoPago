package com.proyecto.servicios.service.exception;

import java.util.Objects;

public class AutenticacionException extends RuntimeException {
    private final Tipo tipo;

    public AutenticacionException(Tipo tipo) {
        super(Objects.requireNonNull(tipo, "tipo").name());
        this.tipo = tipo;
    }

    public Tipo getTipo() {
        return tipo;
    }

    public enum Tipo {
        USUARIO_DUPLICADO,
        CREDENCIALES_INVALIDAS,
        CUENTA_INACTIVA
    }
}
