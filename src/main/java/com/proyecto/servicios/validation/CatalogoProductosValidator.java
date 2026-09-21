package com.proyecto.servicios.validation;

import com.proyecto.servicios.model.ProductoResponse;
import com.proyecto.servicios.model.CatalogoProductosResponse;

import java.util.List;

public final class CatalogoProductosValidator {

    private CatalogoProductosValidator() {
    }

    public static boolean esValido(List<ProductoResponse> productos) {
        return productos != null
                && !productos.isEmpty()
                && productos.stream().allMatch(CatalogoProductosValidator::esProductoValido);
    }

    public static boolean esValido(CatalogoProductosResponse catalogo) {
        if (catalogo == null || !esValido(catalogo.getProductos())) {
            return false;
        }
        return Integer.valueOf(0).equals(catalogo.getCodigo())
                && catalogo.getTotal() != null
                && catalogo.getTotal() == catalogo.getProductos().size();
    }

    public static boolean esProductoValido(ProductoResponse producto) {
        return producto != null
                && producto.getProducto() != null && !producto.getProducto().isBlank()
                && producto.getServicio() != null && !producto.getServicio().isBlank()
                && producto.getIdServicio() != null
                && producto.getIdProducto() != null;
    }
}
