package com.proyecto.servicios.models.response;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
public class CatalogoProductosResponse extends GenericResponse<Void> {
    private Integer total;
    private List<ProductoResponse> productos;
}
