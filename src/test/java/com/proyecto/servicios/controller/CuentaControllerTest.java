package com.proyecto.servicios.controller;

import com.proyecto.servicios.controller.advice.GlobalExceptionHandler;
import com.proyecto.servicios.models.response.CuentaResponse;
import com.proyecto.servicios.models.response.ListaGenericResponse;
import com.proyecto.servicios.service.CuentaService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class CuentaControllerTest {

    private static final String NUMERO = "000000000000000001";
    private final CuentaService service = mock(CuentaService.class);
    private final UsernamePasswordAuthenticationToken usuario =
            new UsernamePasswordAuthenticationToken("7", null, List.of());
    private MockMvc mvc;

    @BeforeEach
    void preparar() {
        mvc = MockMvcBuilders.standaloneSetup(new CuentaController(service))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @Test
    void consultaUsaIdentidadAutenticadaYPaginacionPredeterminada() throws Exception {
        when(service.consultar(any(), eq(7)))
                .thenReturn(new ListaGenericResponse<>(List.of(), 0, 20, 0, 0));

        mvc.perform(post("/cuentas/consultar").principal(usuario)
                .contentType("application/json").content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("datos.pagina").value(0))
                .andExpect(jsonPath("datos.tamanoPagina").value(20));

        verify(service).consultar(argThat(r -> r.pagina() == 0 && r.tamanio() == 20), eq(7));
    }

    @Test
    void consultaDevuelveSaldoYFechasAdemasDeLaIdentificacionDeCuenta() throws Exception {
        CuentaResponse cuenta = new CuentaResponse(4, 3, NUMERO, new BigDecimal("1250.75"), true,
                Instant.parse("2026-10-01T12:00:00Z"), Instant.parse("2026-10-02T12:00:00Z"));
        when(service.consultar(any(), eq(7)))
                .thenReturn(new ListaGenericResponse<>(List.of(cuenta), 0, 20, 1, 1));

        mvc.perform(post("/cuentas/consultar").principal(usuario)
                .contentType("application/json").content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("codigo").value(0))
                .andExpect(jsonPath("datos.datos[0].id").value(4))
                .andExpect(jsonPath("datos.datos[0].clienteId").value(3))
                .andExpect(jsonPath("datos.datos[0].numeroCuenta").value(NUMERO))
                .andExpect(jsonPath("datos.datos[0].saldo").value(1250.75))
                .andExpect(jsonPath("datos.datos[0].estaActiva").value(true))
                .andExpect(jsonPath("datos.datos[0].fechaCreacion").isNotEmpty())
                .andExpect(jsonPath("datos.datos[0].fechaActualizacion").isNotEmpty());
    }

    @Test
    void endpointDeDetalleYaNoEstaDisponible() throws Exception {
        mvc.perform(post("/cuentas/detalle").principal(usuario).contentType("application/json")
                .content("{\"numeroCuenta\":\"" + NUMERO + "\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("codigo").value(1));
        verifyNoInteractions(service);
    }

    @Test
    void endpointDeSaldoYaNoEstaDisponible() throws Exception {
        mvc.perform(post("/cuentas/saldo").principal(usuario).contentType("application/json")
                .content("{\"numeroCuenta\":\"" + NUMERO + "\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("codigo").value(1))
                .andExpect(jsonPath("$").value(org.hamcrest.Matchers.aMapWithSize(2)));

        verifyNoInteractions(service);
    }

    @Test
    void numeroCuentaInvalidoNoLlegaAlServicio() throws Exception {
        mvc.perform(post("/cuentas/consultar").principal(usuario).contentType("application/json")
                .content("{\"numeroCuenta\":\"abc\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("codigo").value(1));

        verifyNoInteractions(service);
    }

    @Test
    void cambioDeEstadoConservaLaOperacionYUsaLaRespuestaExitosa() throws Exception {
        when(service.cambiarEstado(NUMERO, false, 7))
                .thenReturn(new CuentaResponse(4, 3, NUMERO, BigDecimal.TEN, false, null, null));
        mvc.perform(patch("/cuentas/estado").principal(usuario).contentType("application/json")
                .content("{\"numeroCuenta\":\"" + NUMERO + "\",\"activa\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("codigo").value(0))
                .andExpect(jsonPath("datos.estaActiva").value(false))
                .andExpect(jsonPath("$").value(org.hamcrest.Matchers.aMapWithSize(3)));
        verify(service).cambiarEstado(NUMERO, false, 7);
    }

    @Test
    void estadoEsObligatorioEnCambioDeCuenta() throws Exception {
        mvc.perform(patch("/cuentas/estado").principal(usuario).contentType("application/json")
                .content("{\"numeroCuenta\":\"" + NUMERO + "\"}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(service);
    }

    @Test
    void saldoNegativoYTamanioExcesivoNoLleganAlServicio() throws Exception {
        mvc.perform(post("/cuentas/consultar").principal(usuario).contentType("application/json")
                .content("{\"saldo\":-1}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/cuentas/consultar").principal(usuario).contentType("application/json")
                .content("{\"tamanio\":101}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(service);
    }

    @Test
    void noPermiteEnviarElIdDeOtroPropietario() throws Exception {
        mvc.perform(post("/cuentas/consultar").principal(usuario).contentType("application/json")
                .content("{\"clienteId\":9}"))
                .andExpect(status().isBadRequest());
        mvc.perform(patch("/cuentas/estado").principal(usuario).contentType("application/json")
                .content("{\"numeroCuenta\":\"" + NUMERO + "\",\"activa\":false,\"clienteId\":9}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(service);
    }

    @Test
    void rutasConIdentificadoresYaNoExisten() throws Exception {
        mvc.perform(get("/cuentas/" + NUMERO).principal(usuario))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/cuentas/" + NUMERO).principal(usuario))
                .andExpect(status().isNotFound());

        verifyNoInteractions(service);
    }
}
