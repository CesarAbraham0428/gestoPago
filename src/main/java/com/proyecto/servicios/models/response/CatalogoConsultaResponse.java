package com.proyecto.servicios.models.response;

import java.util.List;

public record CatalogoConsultaResponse(int total, List<ProductoResponse> productos) {
    public static CatalogoConsultaResponse desde(CatalogoProductosResponse catalogo) {
        return new CatalogoConsultaResponse(catalogo.getProductos().size(), catalogo.getProductos());
    }
}
