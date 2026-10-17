package com.proyecto.servicios.entity.sf;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
public enum EstadoCivil {
    Soltero("Soltero"), Casado("Casado"), Divorciado("Divorciado"),
    Viudo("Viudo"), Union_libre("Union libre");
    private final String valor;
    EstadoCivil(String valor) { this.valor = valor; }
    @JsonValue public String getValor() { return valor; }
    @JsonCreator public static EstadoCivil parse(String valor) {
        for (EstadoCivil estado : values()) if (estado.valor.equals(valor)) return estado;
        throw new IllegalArgumentException("Estado civil inválido");
    }
}
