package com.proyecto.servicios.controller;

import com.proyecto.servicios.models.response.CatalogosResponse;
import com.proyecto.servicios.models.response.GenericResponse;
import com.proyecto.servicios.service.CatalogoService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CatalogoController {

    private final CatalogoService service;

    public CatalogoController(CatalogoService service) {
        this.service = service;
    }

    /** Pública: el formulario de registro necesita las listas antes de que exista una sesión. */
    @GetMapping(value = "/catalogos", produces = MediaType.APPLICATION_JSON_VALUE)
    public GenericResponse<CatalogosResponse> obtener() {
        return GenericResponse.exito("Catálogos consultados", service.obtener());
    }
}
