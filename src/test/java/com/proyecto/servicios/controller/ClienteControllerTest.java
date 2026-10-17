package com.proyecto.servicios.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.proyecto.servicios.controller.advice.GlobalExceptionHandler;
import com.proyecto.servicios.models.response.ClienteResponse;
import com.proyecto.servicios.models.response.ListaGenericResponse;
import com.proyecto.servicios.service.ClienteService;
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

class ClienteControllerTest {
    private ClienteService service;
    private MockMvc mvc;
    private final UsernamePasswordAuthenticationToken autenticacion =
            new UsernamePasswordAuthenticationToken("9", null, List.of());

    @BeforeEach
    void preparar() {
        service = mock(ClienteService.class);
        mvc = MockMvcBuilders.standaloneSetup(new ClienteController(service))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @Test
    void formatoDeRespuestaNoAdmitidoNoEjecutaLaActualizacion() throws Exception {
        mvc.perform(patch("/clientes").principal(autenticacion)
                .accept("application/xml").contentType("application/json")
                .content("""
                    {"identificacion":{"rfc":"PERA900101AB1"},
                     "cambios":{"telefonoMovil":"5512345678"}}
                    """))
                .andExpect(status().isNotAcceptable())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("codigo").value(1));
        verifyNoInteractions(service);
    }

    @Test
    void patchUsaIdentificacionDelRequestYUsuarioAutenticado() throws Exception {
        ClienteResponse actualizado = new ObjectMapper().findAndRegisterModules().readValue(
                "{\"id\":7,\"telefonoMovil\":\"5512345678\"}", ClienteResponse.class);
        when(service.actualizarParcial(any(), eq(9))).thenReturn(actualizado);
        mvc.perform(patch("/clientes").principal(autenticacion).contentType("application/json")
                .content("""
                    {"identificacion":{"rfc":"PERA900101AB1"},
                     "cambios":{"telefonoMovil":"5512345678"}}
                    """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("datos.id").value(7))
                .andExpect(jsonPath("datos.telefonoMovil").value("5512345678"));
        verify(service).actualizarParcial(argThat(request ->
                request.identificacion().rfc().equals("PERA900101AB1")
                && request.cambios().size() == 1
                && request.cambios().get("telefonoMovil").asText().equals("5512345678")), eq(9));
    }

    @Test
    void consultaAgrupaFiltrosConPaginacionPredeterminada() throws Exception {
        when(service.consultar(any(), eq(9)))
                .thenReturn(new ListaGenericResponse<>(List.of(), 0, 20, 0, 0));
        mvc.perform(post("/clientes/consultar").principal(autenticacion)
                .contentType("application/json").content("{\"nombre\":\"Ana\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("datos.datos").isEmpty())
                .andExpect(jsonPath("datos.tamanoPagina").value(20));
        verify(service).consultar(argThat(request -> request.getNombre().equals("Ana")
                && request.getPagina() == 0 && request.getTamanio() == 20), eq(9));
    }

    @Test
    void consultaDevuelveInformacionPersonalContactoDomicilioCuentasYUsuario() throws Exception {
        ClienteResponse cliente = new ObjectMapper().findAndRegisterModules().readValue("""
                {"id":7,"primerNombre":"Ana","segundoNombre":"Maria","apellidoPaterno":"Perez",
                 "apellidoMaterno":"Ruiz","fechaNacimiento":"1990-01-01","curp":"PERA900101MDFRZN01",
                 "rfc":"PERA900101AB1","sexo":"Femenino","nacionalidad":"Mexicana","estadoCivil":"Soltero",
                 "correoElectronico":"ana@example.com","telefonoMovil":"5512345678",
                 "telefonoAlternativo":"5587654321","ocupacion":"Ingeniera","empresa":"Empresa",
                 "ingresoMensual":12000.50,"rol":"Cliente","activo":true,
                 "fechaCreacion":"2026-10-01T12:00:00Z","fechaActualizacion":"2026-10-02T12:00:00Z",
                 "domicilio":{"id":8,"calle":"Reforma","numeroExterior":"10","colonia":"Centro",
                     "municipio":"Cuauhtemoc","estado":"CDMX","codigoPostal":"06000","pais":"Mexico"},
                 "cuentas":[{"id":10,"clienteId":7,"numeroCuenta":"000000000000000001","saldo":1250.75,
                     "estaActiva":true,"fechaCreacion":"2026-10-01T12:00:00Z",
                     "fechaActualizacion":"2026-10-02T12:00:00Z"}],
                 "usuario":{"id":9,"clienteId":7,"correo":"ana@example.com","activo":true,
                     "fechaCreacion":"2026-10-01T12:00:00Z","fechaActualizacion":"2026-10-02T12:00:00Z"}}
                """, ClienteResponse.class);
        when(service.consultar(any(), eq(9)))
                .thenReturn(new ListaGenericResponse<>(List.of(cliente), 0, 20, 1, 1));

        mvc.perform(post("/clientes/consultar").principal(autenticacion)
                .contentType("application/json").content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("codigo").value(0))
                .andExpect(jsonPath("datos.datos[0].curp").value("PERA900101MDFRZN01"))
                .andExpect(jsonPath("datos.datos[0].rfc").value("PERA900101AB1"))
                .andExpect(jsonPath("datos.datos[0].fechaNacimiento").isNotEmpty())
                .andExpect(jsonPath("datos.datos[0].correoElectronico").value("ana@example.com"))
                .andExpect(jsonPath("datos.datos[0].telefonoMovil").value("5512345678"))
                .andExpect(jsonPath("datos.datos[0].telefonoAlternativo").value("5587654321"))
                .andExpect(jsonPath("datos.datos[0].ocupacion").value("Ingeniera"))
                .andExpect(jsonPath("datos.datos[0].empresa").value("Empresa"))
                .andExpect(jsonPath("datos.datos[0].ingresoMensual").value(12000.50))
                .andExpect(jsonPath("datos.datos[0].fechaCreacion").isNotEmpty())
                .andExpect(jsonPath("datos.datos[0].fechaActualizacion").isNotEmpty())
                .andExpect(jsonPath("datos.datos[0].domicilio.calle").value("Reforma"))
                .andExpect(jsonPath("datos.datos[0].cuentas[0].saldo").value(1250.75))
                .andExpect(jsonPath("datos.datos[0].cuentas[0].fechaCreacion").isNotEmpty())
                .andExpect(jsonPath("datos.datos[0].cuentas[0].fechaActualizacion").isNotEmpty())
                .andExpect(jsonPath("datos.datos[0].usuario.correo").value("ana@example.com"))
                .andExpect(jsonPath("datos.datos[0].usuario.passwordHash").doesNotExist())
                .andExpect(jsonPath("datos.datos[0].usuario.password").doesNotExist());
    }

    @Test
    void identificacionAusenteEs400SinConsultarServicio() throws Exception {
        mvc.perform(post("/clientes/desactivar").principal(autenticacion)
                .contentType("application/json").content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("codigo").value(1));
        verifyNoInteractions(service);
    }

    @Test
    void camposDeIdentificacionDesconocidosSon400() throws Exception {
        mvc.perform(post("/clientes/desactivar").principal(autenticacion)
                .contentType("application/json").content("{\"id\":7}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test
    void endpointDeDetalleYaNoEstaDisponible() throws Exception {
        mvc.perform(post("/clientes/detalle").principal(autenticacion)
                .contentType("application/json").content("{\"rfc\":\"PERA900101AB1\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("codigo").value(1));
        verifyNoInteractions(service);
    }

    @Test
    void filtrosFueraDeContratoSon400() throws Exception {
        mvc.perform(post("/clientes/consultar").principal(autenticacion)
                .contentType("application/json").content("{\"id\":7}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/clientes/consultar").principal(autenticacion)
                .contentType("application/json").content("{\"clienteId\":7}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/clientes/consultar").principal(autenticacion)
                .contentType("application/json").content("{\"rol\":\"Administrador\"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test
    void consultaCombinaRfcCurpYCorreoSinUsarId() throws Exception {
        when(service.consultar(any(), eq(9)))
                .thenReturn(new ListaGenericResponse<>(List.of(), 0, 20, 0, 0));
        mvc.perform(post("/clientes/consultar").principal(autenticacion).contentType("application/json")
                .content("""
                    {"rfc":"PERA900101AB1","curp":"PERA900101MDFRZN01","correo":"ana@example.com"}
                    """))
                .andExpect(status().isOk());
        verify(service).consultar(argThat(r -> r.getRfc().equals("PERA900101AB1")
                && r.getCurp().equals("PERA900101MDFRZN01") && r.getCorreo().equals("ana@example.com")), eq(9));
    }

    @Test
    void validaLimitesYFechasAntesDeConsultarServicio() throws Exception {
        mvc.perform(post("/clientes/consultar").principal(autenticacion)
                .contentType("application/json").content("{\"tamanio\":101}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/clientes/consultar").principal(autenticacion)
                .contentType("application/json").content("{\"desde\":\"2026-10-14\",\"hasta\":\"2026-10-13\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/clientes/consultar").principal(autenticacion)
                .contentType("application/json").content("{\"hasta\":\"+999999999-12-31\"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test
    void consultaPorParametrosYaNoEstaDisponible() throws Exception {
        mvc.perform(get("/clientes").principal(autenticacion).param("nombre", "Ana"))
                .andExpect(status().isMethodNotAllowed());
        verifyNoInteractions(service);
    }

    @Test
    void putEnColeccionDevuelve405ConMetodosPermitidos() throws Exception {
        mvc.perform(put("/clientes").contentType("application/json").content("{}"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().exists("Allow"))
                .andExpect(jsonPath("codigo").value(1));
        verifyNoInteractions(service);
    }

    @Test
    void rutasPorIdYaNoExponenOperaciones() throws Exception {
        mvc.perform(patch("/clientes/7").principal(autenticacion)
                .contentType("application/json").content("{}"))
                .andExpect(status().isNotFound());
        verifyNoInteractions(service);
    }
}
