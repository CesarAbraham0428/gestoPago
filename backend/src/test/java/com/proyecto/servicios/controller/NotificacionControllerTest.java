package com.proyecto.servicios.controller;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.proyecto.servicios.controller.advice.GlobalExceptionHandler;
import com.proyecto.servicios.models.response.ListaGenericResponse;
import com.proyecto.servicios.models.response.NotificacionResponse;
import com.proyecto.servicios.models.response.NotificacionesResponse;
import com.proyecto.servicios.service.NotificacionService;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class NotificacionControllerTest {

    private final NotificacionService service = mock(NotificacionService.class);
    private final UsernamePasswordAuthenticationToken usuario =
            new UsernamePasswordAuthenticationToken("7", null, List.of());
    private MockMvc mvc;

    @BeforeEach
    void preparar() {
        mvc = MockMvcBuilders.standaloneSetup(new NotificacionController(service))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @Test
    void listaLasNotificacionesDelUsuarioDelToken() throws Exception {
        var n = new NotificacionResponse(1, "PAGO_APROBADO", "Pago aprobado", "m", "UPC1", false, Instant.EPOCH);
        when(service.listar(7, 0, 20)).thenReturn(
                new NotificacionesResponse(new ListaGenericResponse<>(List.of(n), 0, 20, 1, 1), 1));

        mvc.perform(post("/notificaciones/consultar").principal(usuario).contentType("application/json").content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("datos.noLeidas").value(1))
                .andExpect(jsonPath("datos.lista.datos[0].titulo").value("Pago aprobado"));
    }

    @Test
    void rechazaUnTamanioDePaginaFueraDeRango() throws Exception {
        mvc.perform(post("/notificaciones/consultar").principal(usuario).contentType("application/json")
                .content("{\"tamanio\":101}")).andExpect(status().isBadRequest());
        mvc.perform(post("/notificaciones/leer").principal(usuario).contentType("application/json")
                .content("{\"id\":0}")).andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test
    void marcaComoLeidaUnaOTodas() throws Exception {
        mvc.perform(post("/notificaciones/leer").principal(usuario).contentType("application/json")
                .content("{\"id\":5}")).andExpect(status().isOk());
        mvc.perform(post("/notificaciones/leer-todas").principal(usuario)).andExpect(status().isOk());

        verify(service).marcarLeida(7, 5);
        verify(service).marcarTodasLeidas(7);
    }
}
