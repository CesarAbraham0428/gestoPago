package com.proyecto.servicios.service;

import com.proyecto.servicios.model.CatalogoProductosResponse;
import com.proyecto.servicios.model.ProductoResponse;

import java.util.List;

public interface CatalogoProductosPersistenceService {
    CatalogoProductosResponse obtenerCatalogo();

    ResultadoActualizacion actualizarSiHayMasProductos(List<ProductoResponse> productosNuevos);

    CatalogoProductosResponse persistirCatalogoRecuperado(List<ProductoResponse> productos);

    record ResultadoActualizacion(
            boolean actualizado,
            long cantidadAnterior,
            CatalogoProductosResponse catalogoPersistido) {
    }
}
