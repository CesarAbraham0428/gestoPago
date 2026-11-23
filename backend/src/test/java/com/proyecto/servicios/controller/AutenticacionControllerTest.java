package com.proyecto.servicios.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.proyecto.servicios.controller.advice.GlobalExceptionHandler;
import com.proyecto.servicios.models.request.LoginRequest;
import com.proyecto.servicios.models.request.VerificacionCodigoRequest;
import com.proyecto.servicios.models.response.AuthResponse;
import com.proyecto.servicios.models.response.DesafioResponse;
import com.proyecto.servicios.service.AutenticacionService;
import com.proyecto.servicios.service.LimiteAutenticacionService;
import com.proyecto.servicios.service.exception.NegocioException;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class AutenticacionControllerTest {
    private static final String DESAFIO = "A".repeat(43);

    private final AutenticacionService service = mock(AutenticacionService.class);
    private final LimiteAutenticacionService limite = mock(LimiteAutenticacionService.class);
    private MockMvc mvc;

    @BeforeEach
    void preparar() {
        mvc = MockMvcBuilders.standaloneSetup(new AutenticacionController(service, limite))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @Test
    void loginDevuelveElDesafioSinTokenNiContrasena() throws Exception {
        when(service.iniciarSesion(new LoginRequest("ana@example.com", "Segura123!")))
                .thenReturn(new DesafioResponse(DESAFIO, 300000, new DesafioResponse.Enrolamiento("SECRETO", "otpauth://totp/x")));
        mvc.perform(post("/auth/login").contentType("application/json")
                .content("{\"correo\":\"ana@example.com\",\"password\":\"Segura123!\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("codigo").value(0))
                .andExpect(jsonPath("datos.desafioId").value(DESAFIO))
                .andExpect(jsonPath("datos.enrolamiento.secreto").value("SECRETO"))
                .andExpect(jsonPath("datos.enrolamiento.otpauthUri").value("otpauth://totp/x"))
                .andExpect(jsonPath("datos.token").doesNotExist())
                .andExpect(jsonPath("datos.password").doesNotExist());
        verify(limite).verificarInicioDeSesion(any(), eq("ana@example.com"));
    }

    @Test
    void verificarEntregaElTokenDentroDelFormatoComun() throws Exception {
        when(service.verificarCodigo(new VerificacionCodigoRequest(DESAFIO, "123456", true)))
                .thenReturn(new AuthResponse("jwt", "Bearer", 1000, "ana@example.com", "Ana Perez"));
        mvc.perform(post("/auth/verificar").contentType("application/json")
                .content("{\"desafioId\":\"" + DESAFIO + "\",\"codigo\":\"123456\",\"cerrarSesionAnterior\":true}"))
                .andExpect(status().isOk()).andExpect(jsonPath("datos.token").value("jwt"));
    }

    @Test
    void sesionActivaSinConfirmarResponde409() throws Exception {
        when(service.verificarCodigo(any())).thenThrow(new NegocioException(HttpStatus.CONFLICT, "Sesión activa"));
        mvc.perform(post("/auth/verificar").contentType("application/json")
                .content("{\"desafioId\":\"" + DESAFIO + "\",\"codigo\":\"123456\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("codigo").value(1));
    }

    @Test
    void demasiadosIntentosResponde429SinInvocarElServicio() throws Exception {
        doThrow(new NegocioException(HttpStatus.TOO_MANY_REQUESTS, "Demasiados intentos"))
                .when(limite).verificarInicioDeSesion(any(), any());
        mvc.perform(post("/auth/login").contentType("application/json")
                .content("{\"correo\":\"ana@example.com\",\"password\":\"Segura123!\"}"))
                .andExpect(status().isTooManyRequests());
        verifyNoInteractions(service);
    }

    @Test
    void logoutCierraLaSesionDelUsuarioAutenticado() throws Exception {
        mvc.perform(post("/auth/logout")
                .principal(new UsernamePasswordAuthenticationToken("7", null, List.of())))
                .andExpect(status().isOk()).andExpect(jsonPath("codigo").value(0));
        verify(service).cerrarSesion(7);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"correo\":\"invalido\",\"password\":\"Segura123!\"}",
            "{\"correo\":\"ana@example.com\",\"password\":\"Segura123!\",\"rol\":\"Administrador\"}"})
    void loginInvalidoNoInvocaServicio(String body) throws Exception {
        mvc.perform(post("/auth/login").contentType("application/json").content(body))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("codigo").value(1));
        verifyNoInteractions(service);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"desafioId\":\"corto\",\"codigo\":\"123456\"}",
            "{\"desafioId\":\"AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA\",\"codigo\":\"12ab56\"}",
            "{\"desafioId\":\"AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA\",\"codigo\":\"123456\",\"admin\":true}"})
    void verificacionInvalidaNoInvocaServicio(String body) throws Exception {
        mvc.perform(post("/auth/verificar").contentType("application/json").content(body))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("codigo").value(1));
        verifyNoInteractions(service);
    }
}
