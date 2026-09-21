package com.proyecto.servicios.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
public class CatalogoProductosResponse extends GenericResponse {
    private Integer total;
    private List<ProductoResponse> productos;
}