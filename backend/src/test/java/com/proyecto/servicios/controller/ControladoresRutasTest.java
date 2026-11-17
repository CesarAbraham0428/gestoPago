package com.proyecto.servicios.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.proyecto.servicios.controller.advice.GlobalExceptionHandler;
import com.proyecto.servicios.models.response.ListaGenericResponse;
import com.proyecto.servicios.service.ClienteService;
import com.proyecto.servicios.service.CuentaService;
import com.proyecto.servicios.service.LimiteAutenticacionService;
import com.proyecto.servicios.service.LimitePagosService;
import com.proyecto.servicios.service.PagoService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** Rutas de los controladores que no tenían prueba: registro, baja, PIN e historial de pagos. */
class ControladoresRutasTest {

    private static final String JSON = "application/json";
    private static final String CLIENTE = """
            {"primerNombre":"Ana","segundoNombre":"Maria","apellidoPaterno":"Perez","apellidoMaterno":"Ruiz",
             "fechaNacimiento":"1990-01-01","curp":"PERA900101MDFRZN01","rfc":"PERA900101AB1",
             "sexo":"Femenino","nacionalidad":"Mexicana","estadoCivil":"Soltero",
             "correoElectronico":"ana@example.com","telefonoMovil":"5512345678","ocupacion":"Ingeniera",
             "empresa":"Empresa","ingresoMensual":12000,
             "domicilio":{"calle":"Reforma","numeroExterior":"10","colonia":"Centro","municipio":"Cuauhtemoc",
                          "estado":"CDMX","codigoPostal":"06000","pais":"Mexico"},
             "password":"Segura#Clave2026"}""";
    private final UsernamePasswordAuthenticationToken usuario =
            new UsernamePasswordAuthenticationToken("7", null, List.of());

    private MockMvc mvc(Object controlador) {
        return MockMvcBuilders.standaloneSetup(controlador).setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void registrarLimitaPorIpYResponde201() throws Exception {
        ClienteService clientes = mock(ClienteService.class);
        LimiteAutenticacionService limite = mock(LimiteAutenticacionService.class);

        mvc(new ClienteController(clientes, limite))
                .perform(post("/clientes").contentType(JSON).content(CLIENTE).with(r -> {
                    r.setRemoteAddr("10.0.0.5");
                    return r;
                }))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("mensaje").value("Cliente registrado"));

        verify(limite).verificarRegistro("10.0.0.5");
        verify(clientes).registrar(any());
    }

    @Test
    void desactivarUsaLaIdentidadAutenticada() throws Exception {
        ClienteService clientes = mock(ClienteService.class);

        mvc(new ClienteController(clientes, mock(LimiteAutenticacionService.class)))
                .perform(post("/clientes/desactivar").principal(usuario).contentType(JSON)
                        .content("{\"rfc\":\"PERA900101AB1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("mensaje").value("Cliente desactivado"));

        verify(clientes).desactivar(any(), eq(7));
    }

    @Test
    void configurarPinPasaLosTresDatosYElUsuario() throws Exception {
        CuentaService cuentas = mock(CuentaService.class);

        mvc(new CuentaController(cuentas))
                .perform(put("/cuentas/pin").principal(usuario).contentType(JSON)
                        .content("{\"numeroCuenta\":\"000000000000000001\",\"pinActual\":\"1234\","
                                + "\"pinNuevo\":\"5678\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("mensaje").value("PIN de autorización actualizado"));

        verify(cuentas).configurarPin("000000000000000001", "1234", "5678", 7);
    }

    @Test
    void elHistorialAcotaLaPaginacion() throws Exception {
        PagoService pagos = mock(PagoService.class);
        when(pagos.historial(anyInt(), anyInt(), anyInt()))
                .thenReturn(new ListaGenericResponse<>(List.of(), 0, 20, 0, 0));
        MockMvc mvc = mvc(new PagoController(pagos, mock(LimitePagosService.class)));

        mvc.perform(get("/pagos").principal(usuario)).andExpect(status().isOk())
                .andExpect(jsonPath("mensaje").value("Historial consultado"));
        verify(pagos).historial(7, 0, 20);

        mvc.perform(get("/pagos").principal(usuario).param("pagina", "-4").param("tamanio", "1000"))
                .andExpect(status().isOk());
        verify(pagos).historial(7, 0, 100);

        mvc.perform(get("/pagos").principal(usuario).param("tamanio", "0")).andExpect(status().isOk());
        verify(pagos).historial(7, 0, 1);
    }
}
