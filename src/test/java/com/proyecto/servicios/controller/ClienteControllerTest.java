package com.proyecto.servicios.controller;

import com.proyecto.servicios.controller.advice.ApiExceptionHandler;
import com.proyecto.servicios.model.ClienteResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.proyecto.servicios.service.ClienteService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ClienteControllerTest {
    private ClienteService service;
    private MockMvc mvc;
    @BeforeEach void preparar() {
        service = mock(ClienteService.class);
        mvc = MockMvcBuilders.standaloneSetup(new ClienteController(service))
            .setControllerAdvice(new ApiExceptionHandler()).build();
    }
    @Test void putEnColeccionDevuelve405ConMetodosPermitidos() throws Exception {
        mvc.perform(put("/clientes").contentType("application/json").content("{}"))
            .andExpect(status().isMethodNotAllowed())
            .andExpect(header().exists("Allow"))
            .andExpect(jsonPath("codigo").value(1))
            .andExpect(jsonPath("mensaje").value(org.hamcrest.Matchers.containsString("/clientes/{id}")));
        verifyNoInteractions(service);
    }
    @Test void patchEnDetalleLlegaAlServicioConSoloLosCamposEnviados() throws Exception {
        ClienteResponse actualizado = new ObjectMapper().findAndRegisterModules().readValue(
            "{\"id\":7,\"telefonoMovil\":\"5512345678\"}", ClienteResponse.class);
        when(service.actualizarParcial(eq(7), any())).thenReturn(actualizado);
        mvc.perform(patch("/clientes/7").contentType("application/json")
            .content("{\"telefonoMovil\":\"5512345678\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("id").value(7))
            .andExpect(jsonPath("telefonoMovil").value("5512345678"));
        verify(service).actualizarParcial(eq(7), argThat(n -> n.size() == 1
            && n.get("telefonoMovil").asText().equals("5512345678")));
    }
    @Test void putEnDetalleDevuelve405YOfrecePatch() throws Exception {
        mvc.perform(put("/clientes/7").contentType("application/json").content("{}"))
            .andExpect(status().isMethodNotAllowed())
            .andExpect(header().string("Allow", org.hamcrest.Matchers.containsString("PATCH")));
        verifyNoInteractions(service);
    }
}
