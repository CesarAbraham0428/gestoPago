package com.proyecto.servicios.controller;

import com.proyecto.servicios.controller.advice.GlobalExceptionHandler;
import com.proyecto.servicios.models.request.LoginRequest;
import com.proyecto.servicios.models.response.AuthResponse;
import com.proyecto.servicios.service.AutenticacionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AutenticacionControllerTest {
    private final AutenticacionService service = mock(AutenticacionService.class);
    private MockMvc mvc;

    @BeforeEach void preparar() {
        mvc = MockMvcBuilders.standaloneSetup(new AutenticacionController(service))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @Test void loginDevuelveTokenDentroDelFormatoComun() throws Exception {
        when(service.iniciarSesion(new LoginRequest("ana@example.com", "Segura123!")))
                .thenReturn(new AuthResponse("jwt", "Bearer", 1000, "ana@example.com", "Ana Perez"));
        mvc.perform(post("/auth/login").contentType("application/json")
                .content("{\"correo\":\"ana@example.com\",\"password\":\"Segura123!\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("codigo").value(0))
                .andExpect(jsonPath("datos.token").value("jwt"))
                .andExpect(jsonPath("datos.password").doesNotExist());
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"correo\":\"invalido\",\"password\":\"Segura123!\"}",
            "{\"correo\":\"ana@example.com\",\"password\":\"Segura123!\",\"rol\":\"Administrador\"}"})
    void entradaInvalidaNoInvocaServicio(String body) throws Exception {
        mvc.perform(post("/auth/login").contentType("application/json").content(body))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("codigo").value(1));
        verifyNoInteractions(service);
    }
}
