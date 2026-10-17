package com.proyecto.servicios.controller;

import com.proyecto.servicios.models.response.GenericResponse;
import com.proyecto.servicios.models.response.CatalogoConsultaResponse;
import com.proyecto.servicios.service.ProductoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ProductoController {

    private final ProductoService productoService;

    @Autowired
    public ProductoController(ProductoService productoService) {
        this.productoService = productoService;
    }

    @GetMapping(value = "/productos", produces = MediaType.APPLICATION_JSON_VALUE)
    public GenericResponse<CatalogoConsultaResponse> obtenerProductos() {
        return GenericResponse.exito("Catálogo consultado",
                CatalogoConsultaResponse.desde(productoService.obtenerProductos()));
    }
}
