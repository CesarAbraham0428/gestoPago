package com.proyecto.servicios.entity.sf;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
@Converter
public class EstadoCivilConverter implements AttributeConverter<EstadoCivil, String> {
    public String convertToDatabaseColumn(EstadoCivil value) { return value == null ? null : value.getValor(); }
    public EstadoCivil convertToEntityAttribute(String value) { return value == null ? null : EstadoCivil.parse(value); }
}
