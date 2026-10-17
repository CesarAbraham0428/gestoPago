package com.proyecto.servicios.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.proyecto.servicios.config.*;
import com.proyecto.servicios.controller.*;
import com.proyecto.servicios.controller.advice.GlobalExceptionHandler;
import com.proyecto.servicios.security.*;
import com.proyecto.servicios.service.*;
import com.proyecto.servicios.service.Impl.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import javax.sql.DataSource;
import java.time.LocalDate;
import java.time.ZoneId;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Cada caso utiliza exclusivamente una BD temporal creada por scripts/test-postgres.ps1. */
@EnabledIfEnvironmentVariable(named="TEST_DB_URL", matches=".*gestopago_backend_test_[a-zA-Z0-9_]+.*")
@SpringBootTest(classes=ClientesPostgresTest.Config.class)
@AutoConfigureMockMvc
class ClientesPostgresTest {
    @SpringBootConfiguration @EnableAutoConfiguration
    @Import({ConfigDB.class, FlywayConfig.class, SecurityConfiguration.class, JwtTokenService.class, ApiSecurityErrorWriter.class,
        ClienteController.class, CuentaController.class, UsuarioController.class, AutenticacionController.class,
        GlobalExceptionHandler.class,
        ClienteServiceImpl.class, CuentaService.class, AutorizacionService.class,
        UsuarioService.class, AutenticacionServiceImpl.class})
    static class Config {}

    @DynamicPropertySource static void propiedades(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", () -> System.getenv("TEST_DB_URL"));
        r.add("spring.datasource.username", () -> System.getenv("TEST_DB_USERNAME"));
        r.add("spring.datasource.password", () -> System.getenv("TEST_DB_PASSWORD"));
        r.add("gestopago.app.jwt.secret", () -> "test-only-secret-012345678901234567890123456789");
    }
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired DataSource datasource;
    @SpyBean PasswordEncoder encoder;
    private JdbcTemplate jdbc;

    @BeforeEach void aislarDatos() {
        jdbc = new JdbcTemplate(datasource);
        String nombre = jdbc.queryForObject("select current_database()", String.class);
        assertNotNull(nombre);
        assertTrue(nombre.matches("gestopago_backend_test_[a-f0-9]{32}"),
            "La limpieza solo está permitida en una BD temporal de pruebas");
        // No se usa rollback de test: necesitamos comprobar commits, triggers diferidos y rollback del servicio.
        jdbc.execute("TRUNCATE TABLE usuarios, cuentas, domicilios, clientes RESTART IDENTITY CASCADE");
    }

    @Test void registroPersisteClienteDomicilioCuentaActivaYUsuarioConBCrypt() throws Exception {
        JsonNode c = crear(registro("1"));
        int id = c.get("id").asInt();
        assertAll(
            () -> assertEquals("Ana", c.get("primerNombre").asText()),
            () -> assertEquals("Reforma", c.get("domicilio").get("calle").asText()),
            () -> assertEquals("06000", c.get("domicilio").get("codigoPostal").asText()),
            () -> assertEquals(1, c.get("cuentas").size()),
            () -> assertEquals(id, c.get("cuentas").get(0).get("clienteId").asInt()),
            () -> assertEquals(0, c.get("cuentas").get(0).get("saldo").decimalValue().signum()),
            () -> assertTrue(c.get("cuentas").get(0).get("estaActiva").asBoolean()),
            () -> assertTrue(c.get("usuario").get("activo").asBoolean()),
            () -> assertFalse(c.get("fechaCreacion").isNull()),
            () -> assertFalse(c.get("usuario").has("passwordHash")));
        String hash = jdbc.queryForObject("select password_hash from usuarios where cliente_id=?", String.class, id);
        assertNotEquals("Segura123!", hash);
        assertTrue(encoder.matches("Segura123!", hash));
        assertEquals(1, jdbc.queryForObject("select count(*) from domicilios where cliente_id=?", Integer.class, id));
        assertEquals(1, jdbc.queryForObject("select count(*) from usuarios where cliente_id=?", Integer.class, id));
        assertTrue(numero(c).matches("[0-9]{18}"));
    }
    @Test void dosClientesRecibenNumerosDeCuentaDistintos() throws Exception {
        JsonNode primero = crear(registro("1"));
        JsonNode segundo = crear(registro("2"));
        assertNotEquals(numero(primero), numero(segundo));
        assertEquals(2, jdbc.queryForObject("select count(distinct numero_cuenta) from cuentas", Integer.class));
    }
    @ParameterizedTest @ValueSource(strings={"curp", "rfc", "correoElectronico"})
    void rechazaDuplicadosSinCrearRegistrosParciales(String campo) throws Exception {
        ObjectNode original = registro("1"); crear(original);
        ObjectNode duplicado = registro("2"); duplicado.set(campo, original.get(campo));
        mvc.perform(post("/clientes").contentType("application/json").content(duplicado.toString()))
            .andExpect(status().isConflict()).andExpect(jsonPath("codigo").value(1));
        assertEquals(1, jdbc.queryForObject("select count(*) from clientes", Integer.class));
        assertEquals(1, jdbc.queryForObject("select count(*) from cuentas", Integer.class));
        assertEquals(1, jdbc.queryForObject("select count(*) from usuarios", Integer.class));
    }
    @Test void falloAlCrearUsuarioRevierteClienteDomicilioYCuenta() throws Exception {
        doReturn("hash-invalido").when(encoder).encode(anyString());
        try {
            mvc.perform(post("/clientes").contentType("application/json").content(registro("1").toString()))
                .andExpect(status().isBadRequest());
            for (String tabla : new String[]{"clientes", "domicilios", "cuentas", "usuarios"}) {
                assertEquals(0, jdbc.queryForObject("select count(*) from " + tabla, Integer.class), tabla);
            }
        } finally { reset(encoder); }
    }
    @Test void admiteLaEdadExactaDe18Anos() throws Exception {
        ObjectNode body = registro("1").put("fechaNacimiento", hoy().minusYears(18).toString());
        crear(body);
        assertEquals(1, jdbc.queryForObject("select count(*) from clientes", Integer.class));
    }
    @Test void rechazaAlClienteQueCumple18AnosManana() throws Exception {
        ObjectNode body = registro("1").put("fechaNacimiento", hoy().minusYears(18).plusDays(1).toString());
        mvc.perform(post("/clientes").contentType("application/json").content(body.toString()))
            .andExpect(status().isBadRequest());
        assertEquals(0, jdbc.queryForObject("select count(*) from clientes", Integer.class));
    }
    @ParameterizedTest @ValueSource(strings={"curp", "rfc", "correo", "numeroCuenta"})
    void consultaPorFiltroDevuelveInformacionCompletaDelClienteCorrecto(String filtro) throws Exception {
        JsonNode c = crear(registro("1")); crear(registro("2"));
        String valor = switch(filtro) {
            case "correo" -> "ANA1@EXAMPLE.COM";
            case "numeroCuenta" -> numero(c);
            default -> c.get(filtro).asText();
        };
        mvc.perform(post("/clientes/consultar").contentType("application/json")
            .content(mapper.createObjectNode().put(filtro, valor).toString()).header("Authorization", bearer("1")))
            .andExpect(status().isOk()).andExpect(jsonPath("datos.totalElementos").value(1))
            .andExpect(jsonPath("datos.datos[0].id").value(c.get("id").asInt()))
            .andExpect(jsonPath("datos.datos[0].curp").value(c.get("curp").asText()))
            .andExpect(jsonPath("datos.datos[0].rfc").value(c.get("rfc").asText()))
            .andExpect(jsonPath("datos.datos[0].correoElectronico").value("ana1@example.com"))
            .andExpect(jsonPath("datos.datos[0].telefonoMovil").value("5512345678"))
            .andExpect(jsonPath("datos.datos[0].fechaNacimiento").value("1990-01-01"))
            .andExpect(jsonPath("datos.datos[0].ingresoMensual").value(12000.50))
            .andExpect(jsonPath("datos.datos[0].empresa").value("Empresa"))
            .andExpect(jsonPath("datos.datos[0].fechaCreacion").isNotEmpty())
            .andExpect(jsonPath("datos.datos[0].fechaActualizacion").isNotEmpty())
            .andExpect(jsonPath("datos.datos[0].domicilio.calle").value("Reforma"))
            .andExpect(jsonPath("datos.datos[0].domicilio.codigoPostal").value("06000"))
            .andExpect(jsonPath("datos.datos[0].cuentas[0].numeroCuenta").value(numero(c)))
            .andExpect(jsonPath("datos.datos[0].cuentas[0].saldo").value(0))
            .andExpect(jsonPath("datos.datos[0].cuentas[0].fechaCreacion").isNotEmpty())
            .andExpect(jsonPath("datos.datos[0].cuentas[0].fechaActualizacion").isNotEmpty())
            .andExpect(jsonPath("datos.datos[0].usuario.correo").value("ana1@example.com"))
            .andExpect(jsonPath("datos.datos[0].usuario.activo").value(true))
            .andExpect(jsonPath("datos.datos[0].usuario.passwordHash").doesNotExist())
            .andExpect(jsonPath("datos.datos[0].usuario.password").doesNotExist());
    }
    @Test void administradorPaginaTodosLosClientesConInformacionCompleta() throws Exception {
        JsonNode primero = crear(registro("1")); JsonNode segundo = crear(registro("2"));
        promoverAdministrador(primero);
        String token = bearer("1");
        mvc.perform(post("/clientes/consultar").contentType("application/json")
            .content("{\"tamanio\":1}").header("Authorization", token))
            .andExpect(status().isOk()).andExpect(jsonPath("datos.totalElementos").value(2))
            .andExpect(jsonPath("datos.pagina").value(0)).andExpect(jsonPath("datos.tamanoPagina").value(1))
            .andExpect(jsonPath("datos.totalPaginas").value(2))
            .andExpect(jsonPath("datos.datos[0].id").value(primero.get("id").asInt()))
            .andExpect(jsonPath("datos.datos[0].curp").value(primero.get("curp").asText()))
            .andExpect(jsonPath("datos.datos[0].usuario.correo").value("ana1@example.com"));
        mvc.perform(post("/clientes/consultar").contentType("application/json")
            .content("{\"pagina\":1,\"tamanio\":1}").header("Authorization", token))
            .andExpect(status().isOk()).andExpect(jsonPath("datos.datos[0].id").value(segundo.get("id").asInt()))
            .andExpect(jsonPath("datos.datos[0].curp").value(segundo.get("curp").asText()))
            .andExpect(jsonPath("datos.datos[0].usuario.correo").value("ana2@example.com"))
            .andExpect(jsonPath("datos.datos[0].cuentas[0].numeroCuenta").value(numero(segundo)));
    }
    @Test void consultaPorRequestAdmiteNombreYRestringeResultadosAlPropietario() throws Exception {
        JsonNode propio = crear(registro("1")); crear(registro("2"));
        mvc.perform(post("/clientes/consultar").header("Authorization", bearer("1"))
            .contentType("application/json").content("{\"nombre\":\"Ana\",\"pagina\":0,\"tamanio\":20}"))
            .andExpect(status().isOk()).andExpect(jsonPath("datos.totalElementos").value(1))
            .andExpect(jsonPath("datos.datos[0].id").value(propio.get("id").asInt()));
    }
    @Test void filtroActivoExcluyeClientesDesactivados() throws Exception {
        JsonNode activo = crear(registro("1")); JsonNode inactivo = crear(registro("2"));
        promoverAdministrador(activo);
        String token = bearer("1");
        mvc.perform(post("/clientes/desactivar").header("Authorization", token)
            .contentType("application/json").content(identificacion(inactivo).toString()))
            .andExpect(status().isOk());
        mvc.perform(post("/clientes/consultar").contentType("application/json")
            .content("{\"activo\":true}").header("Authorization", token))
            .andExpect(status().isOk()).andExpect(jsonPath("datos.totalElementos").value(1))
            .andExpect(jsonPath("datos.datos[0].id").value(activo.get("id").asInt()));
    }
    @Test void rangoDeFechasIncluyeElDiaDeRegistroYExcluyeDiasAnteriores() throws Exception {
        crear(registro("1")); String token = bearer("1");
        mvc.perform(post("/clientes/consultar").contentType("application/json")
            .content(mapper.createObjectNode().put("desde", hoy().toString()).put("hasta", hoy().toString()).toString())
            .header("Authorization", token)).andExpect(status().isOk()).andExpect(jsonPath("datos.totalElementos").value(1));
        mvc.perform(post("/clientes/consultar").contentType("application/json")
            .content(mapper.createObjectNode().put("hasta", hoy().minusDays(1).toString()).toString()).header("Authorization", token))
            .andExpect(status().isOk()).andExpect(jsonPath("datos.datos").isEmpty());
        mvc.perform(post("/clientes/consultar").contentType("application/json")
            .content(mapper.createObjectNode().put("desde", hoy().toString()).put("hasta", hoy().minusDays(1).toString()).toString())
            .header("Authorization", token)).andExpect(status().isBadRequest());
    }
    @Test void patchPersisteSoloLosCambiosYActualizaElCorreoDeAcceso() throws Exception {
        JsonNode c = crear(registro("1")); String token = bearer("1"); int id = c.get("id").asInt();
        JsonNode cambios = mapper.readTree("""
                {"primerNombre":"Luisa","correoElectronico":"NUEVA@example.com","empresa":"Nueva empresa",
                 "domicilio":{"calle":"Insurgentes"}}
                """);
        mvc.perform(patch("/clientes").header("Authorization", token).contentType("application/json")
            .content(actualizacion(c, cambios).toString()))
            .andExpect(status().isOk()).andExpect(jsonPath("datos.correoElectronico").value("nueva@example.com"));
        // Nueva petición y consulta JDBC: no basta con devolver una entidad cambiada en memoria.
        mvc.perform(post("/clientes/consultar").header("Authorization", token)
            .contentType("application/json").content(identificacion(c).toString()))
            .andExpect(status().isOk()).andExpect(jsonPath("datos.totalElementos").value(1))
            .andExpect(jsonPath("datos.datos[0].primerNombre").value("Luisa"))
            .andExpect(jsonPath("datos.datos[0].empresa").value("Nueva empresa"))
            .andExpect(jsonPath("datos.datos[0].domicilio.calle").value("Insurgentes"))
            .andExpect(jsonPath("datos.datos[0].domicilio.codigoPostal").value("06000"))
            .andExpect(jsonPath("datos.datos[0].curp").value(c.get("curp").asText()));
        assertEquals("nueva@example.com", jdbc.queryForObject("select correo from usuarios where cliente_id=?", String.class, id));
        login("nueva@example.com", "Segura123!");
    }
    @ParameterizedTest @ValueSource(strings={"curp", "rfc", "numeroCuenta"})
    void patchRechazaIdentificadoresSinGuardarOtrosCampos(String campo) throws Exception {
        JsonNode c = crear(registro("1"));
        var body = mapper.createObjectNode().put("empresa", "No guardar").put(campo, "cambio");
        mvc.perform(patch("/clientes").header("Authorization", bearer("1"))
            .contentType("application/json").content(actualizacion(c, body).toString()))
            .andExpect(status().isBadRequest());
        assertEquals("Empresa", jdbc.queryForObject("select empresa from clientes where id=?", String.class, c.get("id").asInt()));
    }
    @Test void bajaLogicaConservaDatosDesactivaRelacionadosEInvalidaJwt() throws Exception {
        JsonNode c = crear(registro("1")); String token = bearer("1"); int id = c.get("id").asInt();
        mvc.perform(post("/clientes/desactivar").header("Authorization", token)
            .contentType("application/json").content(identificacion(c).toString()))
            .andExpect(status().isOk());
        assertEquals(1, jdbc.queryForObject("select count(*) from clientes where id=?", Integer.class, id));
        assertFalse(jdbc.queryForObject("select activo from clientes where id=?", Boolean.class, id));
        assertFalse(jdbc.queryForObject("select activo from usuarios where cliente_id=?", Boolean.class, id));
        assertFalse(jdbc.queryForObject("select esta_activa from cuentas where cliente_id=?", Boolean.class, id));
        mvc.perform(post("/clientes/consultar").header("Authorization", token)
            .contentType("application/json").content(identificacion(c).toString()))
            .andExpect(status().isUnauthorized());
        mvc.perform(post("/auth/login").contentType("application/json").content(credenciales("ana1@example.com", "Segura123!")))
            .andExpect(status().isForbidden());
    }
    @Test void cuentaInactivaNoSeReactivaSiElClienteEstaInactivo() throws Exception {
        JsonNode c = crear(registro("1")); JsonNode administrador = crear(registro("2"));
        promoverAdministrador(administrador);
        String token = bearer("2");
        mvc.perform(post("/clientes/desactivar").header("Authorization", token)
            .contentType("application/json").content(identificacion(c).toString()))
            .andExpect(status().isOk());
        mvc.perform(patch("/cuentas/estado").header("Authorization", token)
            .contentType("application/json").content(estadoCuenta(c, true).toString()))
            .andExpect(status().isConflict());
        assertFalse(jdbc.queryForObject("select esta_activa from cuentas where numero_cuenta=?", Boolean.class, numero(c)));
    }
    @Test void consultaCuentaYSaldoPersistidos() throws Exception {
        JsonNode c = crear(registro("1")); String token = bearer("1");
        mvc.perform(post("/cuentas/consultar").header("Authorization", token)
            .contentType("application/json").content(identificacionCuenta(c).toString()))
            .andExpect(status().isOk()).andExpect(jsonPath("datos.totalElementos").value(1))
            .andExpect(jsonPath("datos.datos[0].clienteId").value(c.get("id").asInt()))
            .andExpect(jsonPath("datos.datos[0].numeroCuenta").value(numero(c)))
            .andExpect(jsonPath("datos.datos[0].saldo").value(0))
            .andExpect(jsonPath("datos.datos[0].estaActiva").value(true));
    }
    @Test void filtroDeCuentasActivasExcluyeCuentasDesactivadas() throws Exception {
        JsonNode activa = crear(registro("1")); JsonNode inactiva = crear(registro("2"));
        promoverAdministrador(activa);
        String token = bearer("1");
        mvc.perform(patch("/cuentas/estado").header("Authorization", token)
            .contentType("application/json").content(estadoCuenta(inactiva, false).toString()))
            .andExpect(status().isOk()).andExpect(jsonPath("datos.estaActiva").value(false));
        mvc.perform(post("/cuentas/consultar").header("Authorization", token)
            .contentType("application/json").content("{\"activo\":true}"))
            .andExpect(status().isOk()).andExpect(jsonPath("datos.totalElementos").value(1))
            .andExpect(jsonPath("datos.datos[0].numeroCuenta").value(numero(activa)));
    }
    @ParameterizedTest @ValueSource(strings={"numeroCuenta", "saldo"})
    void filtrosDeCuentaDevuelvenLaCuentaCorrecta(String filtro) throws Exception {
        JsonNode primero = crear(registro("1")); JsonNode segundo = crear(registro("2"));
        jdbc.update("update cuentas set saldo=20 where numero_cuenta=?", numero(segundo));
        promoverAdministrador(primero);
        String valor = filtro.equals("numeroCuenta") ? numero(primero) : "0";
        mvc.perform(post("/cuentas/consultar").header("Authorization", bearer("1"))
            .contentType("application/json").content(mapper.createObjectNode().put(filtro, valor).toString()))
            .andExpect(status().isOk()).andExpect(jsonPath("datos.totalElementos").value(1))
            .andExpect(jsonPath("datos.datos[0].numeroCuenta").value(numero(primero)))
            .andExpect(jsonPath("datos.datos[0].clienteId").value(primero.get("id").asInt()))
            .andExpect(jsonPath("datos.datos[0].saldo").value(0))
            .andExpect(jsonPath("datos.datos[0].estaActiva").value(true))
            .andExpect(jsonPath("datos.datos[0].fechaCreacion").isNotEmpty())
            .andExpect(jsonPath("datos.datos[0].fechaActualizacion").isNotEmpty());
    }
    @ParameterizedTest @ValueSource(strings={"{\"curp\":\"PERA900101MDFRZN09\"}",
        "{\"rfc\":\"PERA900101AB9\"}", "{\"correo\":\"inexistente@example.com\"}",
        "{\"numeroCuenta\":\"999999999999999999\"}"})
    void listasSinCoincidenciasDevuelvenUnaPaginaVacia(String body) throws Exception {
        crear(registro("1"));
        mvc.perform(post("/clientes/consultar").contentType("application/json").content(body)
            .header("Authorization", bearer("1")))
            .andExpect(status().isOk()).andExpect(jsonPath("datos.datos").isEmpty())
            .andExpect(jsonPath("datos.totalElementos").value(0));
    }
    @ParameterizedTest @ValueSource(strings={"numeroCuenta", "saldo"})
    void listasDeCuentasSinCoincidenciasDevuelvenUnaPaginaVacia(String filtro) throws Exception {
        crear(registro("1"));
        ObjectNode body = mapper.createObjectNode();
        if (filtro.equals("numeroCuenta")) {
            body.put("numeroCuenta", "999999999999999999");
        } else {
            body.put("saldo", new java.math.BigDecimal("9999999999999999.99"));
        }
        mvc.perform(post("/cuentas/consultar").header("Authorization", bearer("1"))
            .contentType("application/json").content(body.toString()))
            .andExpect(status().isOk()).andExpect(jsonPath("datos.datos").isEmpty())
            .andExpect(jsonPath("datos.totalElementos").value(0));
    }
    @ParameterizedTest @ValueSource(strings={"/clientes/desactivar", "/cuentas/estado"})
    void operacionesConIdentidadValidaInexistenteDevuelven404(String ruta) throws Exception {
        crear(registro("1"));
        String body = ruta.startsWith("/clientes")
            ? "{\"rfc\":\"PERA900101AB9\"}" : "{\"numeroCuenta\":\"999999999999999999\",\"activa\":false}";
        mvc.perform((ruta.startsWith("/cuentas") ? patch(ruta) : post(ruta)).header("Authorization", bearer("1"))
            .contentType("application/json").content(body))
            .andExpect(status().isNotFound()).andExpect(jsonPath("codigo").value(1));
    }
    @ParameterizedTest @ValueSource(strings={"{\"tamanio\":101}", "{\"pagina\":-1}", "{\"id\":7}", "{\"clienteId\":7}"})
    void filtrosInvalidosDevuelven400(String body) throws Exception {
        crear(registro("1"));
        mvc.perform(post("/clientes/consultar").contentType("application/json").content(body)
            .header("Authorization", bearer("1")))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("codigo").value(1));
    }

    @Test void consultaClientesCombinaRfcCurpYCorreoConAnd() throws Exception {
        JsonNode primero = crear(registro("1")); JsonNode segundo = crear(registro("2"));
        promoverAdministrador(primero);
        String token = bearer("1");
        ObjectNode filtros = mapper.createObjectNode().put("rfc", segundo.get("rfc").asText())
                .put("curp", segundo.get("curp").asText()).put("correo", "ANA2@EXAMPLE.COM");
        mvc.perform(post("/clientes/consultar").contentType("application/json")
                .header("Authorization", token).content(filtros.toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("datos.totalElementos").value(1))
                .andExpect(jsonPath("datos.datos[0].id").value(segundo.get("id").asInt()));
        filtros.put("curp", primero.get("curp").asText());
        mvc.perform(post("/clientes/consultar").contentType("application/json")
                .header("Authorization", token).content(filtros.toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("datos.totalElementos").value(0));
    }
    @ParameterizedTest @ValueSource(strings={"{\"saldo\":-1}", "{\"curp\":\"PERA900101MDFRZN01\"}",
        "{\"rfc\":\"PERA900101AB1\"}", "{\"clienteId\":1}", "{\"numeroCuenta\":\"1\"}",
        "{\"activa\":true,\"activo\":false}"})
    void filtrosDeCuentaInvalidosDevuelven400(String body) throws Exception {
        crear(registro("1"));
        mvc.perform(post("/cuentas/consultar").header("Authorization", bearer("1"))
            .contentType("application/json").content(body))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("codigo").value(1));
    }
    @ParameterizedTest @ValueSource(strings={"/clientes/detalle", "/cuentas/detalle", "/cuentas/saldo"})
    void endpointsDeConsultaRetiradosDevuelven404(String ruta) throws Exception {
        crear(registro("1"));
        mvc.perform(post(ruta).header("Authorization", bearer("1"))
            .contentType("application/json").content("{}"))
            .andExpect(status().isNotFound()).andExpect(jsonPath("codigo").value(1));
    }
    @ParameterizedTest @ValueSource(strings={"/clientes/desactivar", "/cuentas/estado"})
    void requestsSinIdentificacionDevuelven400(String ruta) throws Exception {
        crear(registro("1"));
        mvc.perform((ruta.startsWith("/cuentas") ? patch(ruta) : post(ruta)).header("Authorization", bearer("1"))
            .contentType("application/json").content("{}"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("codigo").value(1));
    }
    @Test void jsonInvalidoDevuelveUnErrorEstandarSinModificarDatos() throws Exception {
        crear(registro("1"));
        mvc.perform(patch("/clientes").header("Authorization", bearer("1"))
            .contentType("application/json").content("{\"identificacion\":"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("codigo").value(1))
            .andExpect(jsonPath("mensaje").isNotEmpty());
        assertEquals("Empresa", jdbc.queryForObject("select empresa from clientes", String.class));
    }

    @Test void validacionDevuelveMensajesEnEspanolPorCampoSinGuardarDatos() throws Exception {
        ObjectNode body = registro("1").put("primerNombre", " ").put("correoElectronico", "correo-invalido");
        ((ObjectNode) body.get("domicilio")).put("codigoPostal", "123");
        mvc.perform(post("/clientes").contentType("application/json").content(body.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("codigo").value(1))
                .andExpect(jsonPath("$").value(org.hamcrest.Matchers.aMapWithSize(2)))
                .andExpect(jsonPath("datos").doesNotHaveJsonPath())
                .andExpect(jsonPath("errores").doesNotHaveJsonPath())
                .andExpect(jsonPath("mensaje").value(org.hamcrest.Matchers.containsString("primerNombre:")))
                .andExpect(jsonPath("mensaje").value(org.hamcrest.Matchers.containsString("Es obligatorio")))
                .andExpect(jsonPath("mensaje").value(org.hamcrest.Matchers.containsString("correoElectronico: El correo electrónico no tiene un formato válido.")))
                .andExpect(jsonPath("mensaje").value(org.hamcrest.Matchers.containsString("domicilio.codigoPostal: El valor no tiene el formato permitido.")));
        assertEquals(0, jdbc.queryForObject("select count(*) from clientes", Integer.class));
    }
    @Test void usuarioNormalNoPuedeConsultarModificarNiDesactivarUnClienteAjeno() throws Exception {
        JsonNode propio = crear(registro("1")); JsonNode ajeno = crear(registro("2"));
        String token = bearer("1");
        mvc.perform(patch("/clientes").header("Authorization", token).contentType("application/json")
            .content(actualizacion(ajeno, mapper.createObjectNode().put("empresa", "No autorizado")).toString()))
            .andExpect(status().isForbidden());
        mvc.perform(post("/clientes/desactivar").header("Authorization", token)
            .contentType("application/json").content(identificacion(ajeno).toString()))
            .andExpect(status().isForbidden());
        mvc.perform(post("/clientes/consultar").header("Authorization", token)
            .contentType("application/json").content(identificacion(ajeno).toString()))
            .andExpect(status().isOk()).andExpect(jsonPath("datos.datos").isEmpty());
        mvc.perform(post("/clientes/consultar").contentType("application/json").content("{}")
            .header("Authorization", token))
            .andExpect(status().isOk()).andExpect(jsonPath("datos.totalElementos").value(1))
            .andExpect(jsonPath("datos.datos[0].id").value(propio.get("id").asInt()));
        assertEquals("Empresa", jdbc.queryForObject("select empresa from clientes where id=?",
            String.class, ajeno.get("id").asInt()));
        assertTrue(jdbc.queryForObject("select activo from clientes where id=?",
            Boolean.class, ajeno.get("id").asInt()));
    }
    @Test void usuarioNormalNoPuedeConsultarNiModificarUnaCuentaAjena() throws Exception {
        JsonNode propio = crear(registro("1")); JsonNode ajeno = crear(registro("2"));
        String token = bearer("1");
        mvc.perform(patch("/cuentas/estado").header("Authorization", token)
            .contentType("application/json").content(estadoCuenta(ajeno, false).toString()))
            .andExpect(status().isNotFound());
        mvc.perform(post("/cuentas/consultar").header("Authorization", token)
            .contentType("application/json").content(identificacionCuenta(ajeno).toString()))
            .andExpect(status().isOk()).andExpect(jsonPath("datos.datos").isEmpty());
        mvc.perform(post("/cuentas/consultar").header("Authorization", token)
            .contentType("application/json").content("{}"))
            .andExpect(status().isOk()).andExpect(jsonPath("datos.totalElementos").value(1))
            .andExpect(jsonPath("datos.datos[0].numeroCuenta").value(numero(propio)));
        assertTrue(jdbc.queryForObject("select esta_activa from cuentas where numero_cuenta=?",
            Boolean.class, numero(ajeno)));
    }
    @Test void cambioPasswordSoloAfectaAlUsuarioPropioYPermiteLoginConLaNueva() throws Exception {
        crear(registro("1")); crear(registro("2")); String token = bearer("1");
        mvc.perform(put("/usuarios/me/password").header("Authorization", token)
            .contentType("application/json").content("{\"passwordActual\":\"Segura123!\",\"passwordNueva\":\"Nueva123!\"}"))
            .andExpect(status().isOk());
        login("ana1@example.com", "Nueva123!");
        login("ana2@example.com", "Segura123!");
        mvc.perform(post("/auth/login").contentType("application/json").content(credenciales("ana1@example.com", "Segura123!")))
            .andExpect(status().isUnauthorized());
    }
    @Test void consultaUsuarioPropioNoExponeHashNiPermiteElegirUnUsuarioAjeno() throws Exception {
        JsonNode propio = crear(registro("1")); JsonNode ajeno = crear(registro("2")); String token = bearer("1");
        mvc.perform(get("/usuarios/me").header("Authorization", token))
            .andExpect(status().isOk()).andExpect(jsonPath("datos.correo").value("ana1@example.com"))
            .andExpect(jsonPath("datos.id").value(propio.get("usuario").get("id").asInt()))
            .andExpect(jsonPath("datos.passwordHash").doesNotExist()).andExpect(jsonPath("datos.password").doesNotExist());
        mvc.perform(get("/usuarios/me").header("Authorization", bearer("2")))
            .andExpect(status().isOk()).andExpect(jsonPath("datos.correo").value("ana2@example.com"))
            .andExpect(jsonPath("datos.id").value(ajeno.get("usuario").get("id").asInt()));
    }

    @Test void administradorCombinaFiltrosDeUsuariosSinExponerCredenciales() throws Exception {
        JsonNode administrador = crear(registro("1")); JsonNode otro = crear(registro("2"));
        promoverAdministrador(administrador);
        String token = bearer("1");
        ObjectNode filtros = mapper.createObjectNode().put("correo", "ANA2@EXAMPLE.COM")
            .put("nombre", "Perez").put("activo", true)
            .put("rfc", otro.get("rfc").asText().toLowerCase(java.util.Locale.ROOT))
            .put("curp", otro.get("curp").asText().toLowerCase(java.util.Locale.ROOT));
        mvc.perform(post("/usuarios/consultar").header("Authorization", token)
            .contentType("application/json").content(filtros.toString()))
            .andExpect(status().isOk()).andExpect(jsonPath("codigo").value(0))
            .andExpect(jsonPath("datos.totalElementos").value(1))
            .andExpect(jsonPath("datos.datos[0].id").value(otro.get("usuario").get("id").asInt()))
            .andExpect(jsonPath("datos.datos[0].passwordHash").doesNotExist())
            .andExpect(jsonPath("datos.datos[0].password").doesNotExist());
        filtros.put("rfc", administrador.get("rfc").asText());
        mvc.perform(post("/usuarios/consultar").header("Authorization", token)
            .contentType("application/json").content(filtros.toString()))
            .andExpect(status().isOk()).andExpect(jsonPath("datos.datos").isEmpty())
            .andExpect(jsonPath("datos.totalElementos").value(0));
    }

    @Test void consultaUsuariosRestringeResultadosYConteoAlPropietario() throws Exception {
        JsonNode propio = crear(registro("1")); JsonNode ajeno = crear(registro("2"));
        String token = bearer("1");
        mvc.perform(post("/usuarios/consultar").header("Authorization", token)
            .contentType("application/json").content("{\"nombre\":\"Ana\",\"tamanio\":1}"))
            .andExpect(status().isOk()).andExpect(jsonPath("datos.totalElementos").value(1))
            .andExpect(jsonPath("datos.totalPaginas").value(1))
            .andExpect(jsonPath("datos.datos[0].id").value(propio.get("usuario").get("id").asInt()));
        mvc.perform(post("/usuarios/consultar").header("Authorization", token)
            .contentType("application/json").content(mapper.createObjectNode()
                .put("rfc", ajeno.get("rfc").asText()).put("curp", ajeno.get("curp").asText()).toString()))
            .andExpect(status().isOk()).andExpect(jsonPath("datos.datos").isEmpty())
            .andExpect(jsonPath("datos.totalElementos").value(0));
    }

    @Test void administradorPaginaUsuariosYFiltraEstadoInactivo() throws Exception {
        JsonNode primero = crear(registro("1")); JsonNode segundo = crear(registro("2"));
        promoverAdministrador(primero);
        String token = bearer("1");
        mvc.perform(post("/usuarios/consultar").header("Authorization", token)
            .contentType("application/json").content("{\"pagina\":1,\"tamanio\":1}"))
            .andExpect(status().isOk()).andExpect(jsonPath("datos.totalElementos").value(2))
            .andExpect(jsonPath("datos.totalPaginas").value(2))
            .andExpect(jsonPath("datos.datos[0].id").value(segundo.get("usuario").get("id").asInt()));
        mvc.perform(post("/clientes/desactivar").header("Authorization", token)
            .contentType("application/json").content(identificacion(segundo).toString()))
            .andExpect(status().isOk());
        mvc.perform(post("/usuarios/consultar").header("Authorization", token)
            .contentType("application/json").content("{\"activo\":false}"))
            .andExpect(status().isOk()).andExpect(jsonPath("datos.totalElementos").value(1))
            .andExpect(jsonPath("datos.datos[0].activo").value(false));
    }

    @ParameterizedTest @ValueSource(strings={"id", "rol", "activo", "fechaCreacion", "saldo", "numeroCuenta"})
    void registroRechazaCamposControladosPorElServidor(String campo) throws Exception {
        ObjectNode body = registro("1").put(campo, "valor-no-permitido");
        mvc.perform(post("/clientes").contentType("application/json").content(body.toString()))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("codigo").value(1))
            .andExpect(jsonPath("datos").doesNotHaveJsonPath());
        assertEquals(0, jdbc.queryForObject("select count(*) from clientes", Integer.class));
    }

    @Test void registroRechazaIdDeDomicilioAnidado() throws Exception {
        ObjectNode body = registro("1");
        ((ObjectNode) body.get("domicilio")).put("id", 9);
        mvc.perform(post("/clientes").contentType("application/json").content(body.toString()))
            .andExpect(status().isBadRequest());
        assertEquals(0, jdbc.queryForObject("select count(*) from clientes", Integer.class));
    }
    @ParameterizedTest @ValueSource(strings={"/clientes", "/usuarios/me"})
    void endpointsProtegidosRechazanSolicitudesSinJwt(String ruta) throws Exception {
        mvc.perform(get(ruta)).andExpect(status().isUnauthorized()).andExpect(jsonPath("codigo").value(1));
    }
    @ParameterizedTest @ValueSource(strings={"/clientes/consultar", "/clientes/desactivar",
        "/cuentas/consultar", "/usuarios/consultar"})
    void endpointsConRequestRechazanSolicitudesSinJwt(String ruta) throws Exception {
        mvc.perform(post(ruta).contentType("application/json").content("{}"))
            .andExpect(status().isUnauthorized()).andExpect(jsonPath("codigo").value(1))
            .andExpect(jsonPath("$").value(org.hamcrest.Matchers.aMapWithSize(2)));
    }
    @ParameterizedTest @ValueSource(strings={
        "update clientes set primer_nombre='Ana1' where id=?", "update clientes set curp='PERA900101MDFRZN09' where id=?",
        "update clientes set rfc='PERA900101AB9' where id=?", "update clientes set telefono_movil='123' where id=?",
        "update clientes set fecha_nacimiento=current_date where id=?", "delete from clientes where id=?",
        "update cuentas set saldo=-1 where id=?", "update cuentas set numero_cuenta='999999999999999999' where id=?",
        "delete from cuentas where id=?"})
    void baseDeDatosImpideSaltarseLasReglasDeNegocio(String sql) throws Exception {
        JsonNode c = crear(registro("1"));
        int id = sql.contains("cuentas") ? c.get("cuentas").get(0).get("id").asInt() : c.get("id").asInt();
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(sql, id));
    }

    private LocalDate hoy() { return LocalDate.now(ZoneId.of("America/Mexico_City")); }
    private String numero(JsonNode cliente) { return cliente.get("cuentas").get(0).get("numeroCuenta").asText(); }
    private ObjectNode identificacion(JsonNode cliente) {
        return mapper.createObjectNode().put("rfc", cliente.get("rfc").asText());
    }
    private ObjectNode identificacionCuenta(JsonNode cliente) {
        return mapper.createObjectNode().put("numeroCuenta", numero(cliente));
    }
    private ObjectNode actualizacion(JsonNode cliente, JsonNode cambios) {
        ObjectNode request = mapper.createObjectNode();
        request.set("identificacion", identificacion(cliente));
        request.set("cambios", cambios);
        return request;
    }
    private ObjectNode estadoCuenta(JsonNode cliente, boolean activa) {
        return identificacionCuenta(cliente).put("activa", activa);
    }
    private void promoverAdministrador(JsonNode cliente) {
        assertEquals(1, jdbc.update("update clientes set rol='Administrador' where id=?", cliente.get("id").asInt()));
    }
    private ObjectNode registro(String sufijo) throws Exception {
        return ((ObjectNode) mapper.readTree("""
            {"primerNombre":"Ana","apellidoPaterno":"Perez","apellidoMaterno":"Ruiz",
             "fechaNacimiento":"1990-01-01","curp":"PERA900101MDFRZN01","rfc":"PERA900101AB1",
             "sexo":"Femenino","nacionalidad":"Argentina","estadoCivil":"Union libre",
             "correoElectronico":"ana1@example.com","telefonoMovil":"5512345678",
             "ocupacion":"Ingeniera","empresa":"Empresa","ingresoMensual":12000.50,
             "domicilio":{"calle":"Reforma","numeroExterior":"10","colonia":"Centro",
             "municipio":"Cuauhtemoc","estado":"Ciudad de Mexico","codigoPostal":"06000","pais":"Mexico"},
             "password":"Segura123!"}
            """ )).put("curp", "PERA900101MDFRZN0" + sufijo).put("rfc", "PERA900101AB" + sufijo)
            .put("correoElectronico", "ana" + sufijo + "@example.com");
    }
    private JsonNode json(MvcResult r) throws Exception {
        JsonNode respuesta = mapper.readTree(r.getResponse().getContentAsString());
        assertEquals(0, respuesta.get("codigo").asInt());
        assertTrue(respuesta.hasNonNull("mensaje"));
        assertEquals(3, respuesta.size());
        assertTrue(respuesta.has("datos"));
        assertFalse(respuesta.has("errores"));
        return respuesta.get("datos");
    }
    private JsonNode crear(ObjectNode body) throws Exception {
        return json(mvc.perform(post("/clientes").contentType("application/json").content(body.toString()))
            .andExpect(status().isCreated()).andReturn());
    }
    private String credenciales(String correo, String password) {
        return mapper.createObjectNode().put("correo", correo).put("password", password).toString();
    }
    private String login(String correo, String password) throws Exception {
        return json(mvc.perform(post("/auth/login").contentType("application/json").content(credenciales(correo, password)))
            .andExpect(status().isOk()).andReturn()).get("token").asText();
    }
    private String bearer(String sufijo) throws Exception { return "Bearer " + login("ana" + sufijo + "@example.com", "Segura123!"); }
}
