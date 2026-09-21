package com.proyecto.servicios.controller;

import com.proyecto.servicios.model.CatalogoProductosResponse;
import com.proyecto.servicios.service.ProductoService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ProductoController {

    private final ProductoService productoService;

    public ProductoController(ProductoService productoService) {
        this.productoService = productoService;
    }

    @GetMapping(value = "/productos", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<CatalogoProductosResponse> obtenerProductos() {
        CatalogoProductosResponse response = productoService.obtenerProductos();
        Integer codigo = response.getCodigo() == null ? 0 : response.getCodigo();
        HttpStatus status = switch (codigo) {
            case 1 -> HttpStatus.BAD_GATEWAY;
            case 2 -> HttpStatus.INTERNAL_SERVER_ERROR;
            default -> HttpStatus.OK;
        };
        return ResponseEntity.status(status).body(response);
    }
}
