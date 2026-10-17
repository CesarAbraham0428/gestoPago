package com.proyecto.servicios.controller;

import com.proyecto.servicios.client.GestoPagoCatalogException;
import com.proyecto.servicios.controller.advice.GlobalExceptionHandler;
import com.proyecto.servicios.models.response.CatalogoProductosResponse;
import com.proyecto.servicios.service.ProductoService;
import com.proyecto.servicios.service.exception.CatalogoPersistenciaException;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.hamcrest.Matchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ProductoControllerTest {
    private final ProductoService service = mock(ProductoService.class);
    private MockMvc mvc;

    @BeforeEach void preparar() {
        mvc = MockMvcBuilders.standaloneSetup(new ProductoController(service))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @Test void catalogoDevuelveFormatoComunSinInformacionDeInfraestructura() throws Exception {
        var catalogo = new CatalogoProductosResponse();
        catalogo.setCodigo(3);
        catalogo.setMensaje("Redis no disponible; datos de PostgreSQL");
        catalogo.setProductos(List.of());
        when(service.obtenerProductos()).thenReturn(catalogo);
        mvc.perform(get("/productos")).andExpect(status().isOk())
                .andExpect(jsonPath("codigo").value(0))
                .andExpect(jsonPath("datos.total").value(0))
                .andExpect(jsonPath("datos.productos").isEmpty())
                .andExpect(content().string(not(containsString("Redis"))))
                .andExpect(content().string(not(containsString("PostgreSQL"))));
    }

    @Test void errorDelProveedorSeProcesaGlobalmenteSinExponerDetalles() throws Exception {
        when(service.obtenerProductos()).thenThrow(new GestoPagoCatalogException("token-secreto"));
        mvc.perform(get("/productos")).andExpect(status().isBadGateway())
                .andExpect(jsonPath("codigo").value(1))
                .andExpect(jsonPath("datos").doesNotHaveJsonPath())
                .andExpect(jsonPath("$").value(aMapWithSize(2)))
                .andExpect(content().string(not(containsString("token-secreto"))));
    }

    @Test void errorDePersistenciaSeProcesaGlobalmenteSinExponerSql() throws Exception {
        when(service.obtenerProductos()).thenThrow(new CatalogoPersistenciaException("SELECT FROM productos"));
        mvc.perform(get("/productos")).andExpect(status().isInternalServerError())
                .andExpect(jsonPath("codigo").value(1))
                .andExpect(content().string(not(containsString("SELECT"))));
    }
}
