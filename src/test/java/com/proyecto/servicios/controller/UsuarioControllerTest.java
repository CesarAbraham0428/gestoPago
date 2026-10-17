package com.proyecto.servicios.controller;

import com.proyecto.servicios.controller.advice.GlobalExceptionHandler;
import com.proyecto.servicios.models.request.CambioPasswordRequest;
import com.proyecto.servicios.models.response.UsuarioResponse;
import com.proyecto.servicios.models.response.ListaGenericResponse;
import com.proyecto.servicios.service.UsuarioService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class UsuarioControllerTest {

    private final UsuarioService service = mock(UsuarioService.class);
    private final UsernamePasswordAuthenticationToken usuario =
            new UsernamePasswordAuthenticationToken("7", null, List.of());
    private MockMvc mvc;

    @BeforeEach
    void preparar() {
        mvc = MockMvcBuilders.standaloneSetup(new UsuarioController(service))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @Test
    void obtenerMeConsultaSolamenteLaIdentidadDelToken() throws Exception {
        when(service.obtener(7)).thenReturn(new UsuarioResponse(7, 3, "ana@example.com", true, null, null));

        mvc.perform(get("/usuarios/me").principal(usuario))
                .andExpect(status().isOk())
                .andExpect(jsonPath("datos.id").value(7));

        verify(service).obtener(7);
    }

    @Test
    void cambiarPasswordNoRecibeIdentificacionEnRutaORequest() throws Exception {
        mvc.perform(put("/usuarios/me/password").principal(usuario).contentType("application/json")
                .content("{\"passwordActual\":\"Actual123!\",\"passwordNueva\":\"Nueva123!\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("codigo").value(0))
                .andExpect(jsonPath("mensaje").value("Contraseña actualizada"))
                .andExpect(jsonPath("$").value(org.hamcrest.Matchers.aMapWithSize(3)))
                .andExpect(jsonPath("datos").value(org.hamcrest.Matchers.nullValue()));

        verify(service).cambiarPassword(7, new CambioPasswordRequest("Actual123!", "Nueva123!"));
    }

    @Test
    void consultaCombinaFiltrosEnUnSoloRequest() throws Exception {
        when(service.consultar(any(), eq(7)))
                .thenReturn(new ListaGenericResponse<>(List.of(), 0, 20, 0, 0));
        mvc.perform(post("/usuarios/consultar").principal(usuario).contentType("application/json")
                .content("{\"rfc\":\"PERA900101AB1\",\"curp\":\"PERA900101MDFRZN01\",\"correo\":\"ana@example.com\",\"nombre\":\"Ana\",\"activo\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("codigo").value(0))
                .andExpect(jsonPath("datos.datos").isEmpty())
                .andExpect(jsonPath("datos.totalElementos").value(0));
        verify(service).consultar(argThat(r -> r.correo().equals("ana@example.com")
                && r.rfc().equals("PERA900101AB1") && r.curp().equals("PERA900101MDFRZN01")
                && r.nombre().equals("Ana") && r.activo() && r.pagina() == 0 && r.tamanio() == 20), eq(7));
    }

    @Test
    void filtrosInvalidosYCuerpoAusenteNoLleganAlServicio() throws Exception {
        for (String body : List.of("{\"id\":7}", "{\"clienteId\":3}", "{\"correo\":\"invalido\"}",
                "{\"rfc\":\"invalido\"}", "{\"curp\":\"invalida\"}",
                "{\"tamanio\":101}", "{\"pagina\":-1}", "{\"passwordHash\":\"secreto\"}")) {
            mvc.perform(post("/usuarios/consultar").principal(usuario).contentType("application/json").content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("codigo").value(1))
                    .andExpect(jsonPath("datos").doesNotHaveJsonPath());
        }
        mvc.perform(post("/usuarios/consultar").principal(usuario).contentType("application/json"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test
    void passwordDebilSeRechazaConBeanValidationSinInvocarServicio() throws Exception {
        mvc.perform(put("/usuarios/me/password").principal(usuario).contentType("application/json")
                .content("{\"passwordActual\":\"Actual123!\",\"passwordNueva\":\"abcdefgh\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("mensaje").value(org.hamcrest.Matchers.containsString("passwordNueva:")))
                .andExpect(jsonPath("$").value(org.hamcrest.Matchers.aMapWithSize(2)));
        verifyNoInteractions(service);
    }

    @Test
    void passwordVaciaNoLlegaAlServicio() throws Exception {
        mvc.perform(put("/usuarios/me/password").principal(usuario).contentType("application/json")
                .content("{\"passwordActual\":\"\",\"passwordNueva\":\"Nueva123!\"}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(service);
    }

    @Test
    void idEnRequestDePasswordEsRechazado() throws Exception {
        mvc.perform(put("/usuarios/me/password").principal(usuario).contentType("application/json")
                .content("{\"id\":9,\"passwordActual\":\"Actual123!\",\"passwordNueva\":\"Nueva123!\"}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(service);
    }

    @Test
    void rutaAnteriorNoPermiteElegirOtroUsuario() throws Exception {
        mvc.perform(get("/usuarios/9").principal(usuario))
                .andExpect(status().isNotFound());
        mvc.perform(put("/usuarios/9/password").principal(usuario).contentType("application/json")
                .content("{\"passwordActual\":\"Actual123!\",\"passwordNueva\":\"Nueva123!\"}"))
                .andExpect(status().isNotFound());

        verifyNoInteractions(service);
    }
}
