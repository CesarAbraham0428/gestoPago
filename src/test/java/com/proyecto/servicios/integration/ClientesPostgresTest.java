package com.proyecto.servicios.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.proyecto.servicios.config.*;
import com.proyecto.servicios.controller.*;
import com.proyecto.servicios.controller.advice.ApiExceptionHandler;
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
    @Import({ConfigDB.class, FlywayConfig.class, SecurityConfiguration.class, JwtTokenService.class,
        ClienteController.class, CuentaController.class, UsuarioController.class, AutenticacionController.class,
        ApiExceptionHandler.class, ClienteServiceImpl.class, CuentaService.class,
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
    @ParameterizedTest @ValueSource(strings={"id", "curp", "rfc", "correo", "numeroCuenta"})
    void consultaPorFiltroDevuelveAlClienteCorrectoConDetalle(String filtro) throws Exception {
        JsonNode c = crear(registro("1")); crear(registro("2"));
        String valor = switch(filtro) {
            case "correo" -> "ANA1@EXAMPLE.COM";
            case "numeroCuenta" -> numero(c);
            default -> c.get(filtro).asText();
        };
        mvc.perform(get("/clientes").param(filtro, valor).header("Authorization", bearer("1")))
            .andExpect(status().isOk()).andExpect(jsonPath("totalElements").value(1))
            .andExpect(jsonPath("content[0].id").value(c.get("id").asInt()))
            .andExpect(jsonPath("content[0].domicilio.calle").value("Reforma"))
            .andExpect(jsonPath("content[0].cuentas[0].saldo").value(0));
    }
    @Test void consultaGlobalPaginaTodosLosClientesSinCredenciales() throws Exception {
        JsonNode primero = crear(registro("1")); JsonNode segundo = crear(registro("2"));
        String token = bearer("1");
        mvc.perform(get("/clientes").param("tamanio", "1").header("Authorization", token))
            .andExpect(status().isOk()).andExpect(jsonPath("totalElements").value(2))
            .andExpect(jsonPath("content[0].id").value(primero.get("id").asInt()))
            .andExpect(jsonPath("content[0].curp").doesNotExist());
        mvc.perform(get("/clientes").param("pagina", "1").param("tamanio", "1").header("Authorization", token))
            .andExpect(status().isOk()).andExpect(jsonPath("content[0].id").value(segundo.get("id").asInt()));
    }
    @Test void filtroActivoExcluyeClientesDesactivados() throws Exception {
        JsonNode activo = crear(registro("1")); JsonNode inactivo = crear(registro("2"));
        String token = bearer("1");
        mvc.perform(delete("/clientes/" + inactivo.get("id").asInt()).header("Authorization", token))
            .andExpect(status().isNoContent());
        mvc.perform(get("/clientes").param("activo", "true").header("Authorization", token))
            .andExpect(status().isOk()).andExpect(jsonPath("totalElements").value(1))
            .andExpect(jsonPath("content[0].id").value(activo.get("id").asInt()));
    }
    @Test void rangoDeFechasIncluyeElDiaDeRegistroYExcluyeDiasAnteriores() throws Exception {
        crear(registro("1")); String token = bearer("1");
        mvc.perform(get("/clientes").param("desde", hoy().toString()).param("hasta", hoy().toString())
            .header("Authorization", token)).andExpect(status().isOk()).andExpect(jsonPath("totalElements").value(1));
        mvc.perform(get("/clientes").param("hasta", hoy().minusDays(1).toString()).header("Authorization", token))
            .andExpect(status().isNotFound());
        mvc.perform(get("/clientes").param("desde", hoy().toString()).param("hasta", hoy().minusDays(1).toString())
            .header("Authorization", token)).andExpect(status().isBadRequest());
    }
    @Test void patchPersisteSoloLosCambiosYActualizaElCorreoDeAcceso() throws Exception {
        JsonNode c = crear(registro("1")); String token = bearer("1"); int id = c.get("id").asInt();
        mvc.perform(patch("/clientes/" + id).header("Authorization", token).contentType("application/json")
            .content("""
                {"primerNombre":"Luisa","correoElectronico":"NUEVA@example.com","empresa":"Nueva empresa",
                 "domicilio":{"calle":"Insurgentes"}}
                """))
            .andExpect(status().isOk()).andExpect(jsonPath("correoElectronico").value("nueva@example.com"));
        // Nueva petición y consulta JDBC: no basta con devolver una entidad cambiada en memoria.
        mvc.perform(get("/clientes/" + id).header("Authorization", token))
            .andExpect(status().isOk()).andExpect(jsonPath("primerNombre").value("Luisa"))
            .andExpect(jsonPath("empresa").value("Nueva empresa"))
            .andExpect(jsonPath("domicilio.calle").value("Insurgentes"))
            .andExpect(jsonPath("domicilio.codigoPostal").value("06000"))
            .andExpect(jsonPath("curp").value(c.get("curp").asText()));
        assertEquals("nueva@example.com", jdbc.queryForObject("select correo from usuarios where cliente_id=?", String.class, id));
        login("nueva@example.com", "Segura123!");
    }
    @ParameterizedTest @ValueSource(strings={"curp", "rfc", "numeroCuenta"})
    void patchRechazaIdentificadoresSinGuardarOtrosCampos(String campo) throws Exception {
        JsonNode c = crear(registro("1"));
        var body = mapper.createObjectNode().put("empresa", "No guardar").put(campo, "cambio");
        mvc.perform(patch("/clientes/" + c.get("id").asInt()).header("Authorization", bearer("1"))
            .contentType("application/json").content(body.toString())).andExpect(status().isBadRequest());
        assertEquals("Empresa", jdbc.queryForObject("select empresa from clientes where id=?", String.class, c.get("id").asInt()));
    }
    @Test void bajaLogicaConservaDatosDesactivaRelacionadosEInvalidaJwt() throws Exception {
        JsonNode c = crear(registro("1")); String token = bearer("1"); int id = c.get("id").asInt();
        mvc.perform(delete("/clientes/" + id).header("Authorization", token)).andExpect(status().isNoContent());
        assertEquals(1, jdbc.queryForObject("select count(*) from clientes where id=?", Integer.class, id));
        assertFalse(jdbc.queryForObject("select activo from clientes where id=?", Boolean.class, id));
        assertFalse(jdbc.queryForObject("select activo from usuarios where cliente_id=?", Boolean.class, id));
        assertFalse(jdbc.queryForObject("select esta_activa from cuentas where cliente_id=?", Boolean.class, id));
        mvc.perform(get("/clientes/" + id).header("Authorization", token)).andExpect(status().isUnauthorized());
        mvc.perform(post("/auth/login").contentType("application/json").content(credenciales("ana1@example.com", "Segura123!")))
            .andExpect(status().isForbidden());
    }
    @Test void cuentaInactivaNoSeReactivaSiElClienteEstaInactivo() throws Exception {
        JsonNode c = crear(registro("1")); crear(registro("2")); String token = bearer("2");
        mvc.perform(delete("/clientes/" + c.get("id").asInt()).header("Authorization", token)).andExpect(status().isNoContent());
        mvc.perform(put("/cuentas/" + numero(c) + "/estado").header("Authorization", token)
            .contentType("application/json").content("{\"activa\":true}"))
            .andExpect(status().isConflict());
        assertFalse(jdbc.queryForObject("select esta_activa from cuentas where numero_cuenta=?", Boolean.class, numero(c)));
    }
    @Test void consultaCuentaYSaldoPersistidos() throws Exception {
        JsonNode c = crear(registro("1")); String token = bearer("1");
        mvc.perform(get("/cuentas/" + numero(c)).header("Authorization", token))
            .andExpect(status().isOk()).andExpect(jsonPath("clienteId").value(c.get("id").asInt()))
            .andExpect(jsonPath("saldo").value(0)).andExpect(jsonPath("estaActiva").value(true));
        mvc.perform(get("/cuentas/" + numero(c) + "/saldo").header("Authorization", token))
            .andExpect(status().isOk()).andExpect(content().string("0.00"));
    }
    @Test void filtroDeCuentasActivasExcluyeCuentasDesactivadas() throws Exception {
        JsonNode activa = crear(registro("1")); JsonNode inactiva = crear(registro("2")); String token = bearer("1");
        mvc.perform(delete("/cuentas/" + numero(inactiva)).header("Authorization", token)).andExpect(status().isNoContent());
        mvc.perform(get("/cuentas").param("activa", "true").header("Authorization", token))
            .andExpect(status().isOk()).andExpect(jsonPath("totalElements").value(1))
            .andExpect(jsonPath("content[0].numeroCuenta").value(numero(activa)));
    }
    @ParameterizedTest @ValueSource(strings={"id", "saldo"})
    void filtrosDeCuentaDevuelvenLaCuentaCorrecta(String filtro) throws Exception {
        JsonNode primero = crear(registro("1")); JsonNode segundo = crear(registro("2"));
        jdbc.update("update cuentas set saldo=20 where numero_cuenta=?", numero(segundo));
        String valor = filtro.equals("id") ? primero.get("cuentas").get(0).get("id").asText() : "0";
        mvc.perform(get("/cuentas").param(filtro, valor).header("Authorization", bearer("1")))
            .andExpect(status().isOk()).andExpect(jsonPath("totalElements").value(1))
            .andExpect(jsonPath("content[0].numeroCuenta").value(numero(primero)))
            .andExpect(jsonPath("content[0].saldo").value(0));
    }
    @ParameterizedTest @ValueSource(strings={"/clientes/2147483647", "/clientes?curp=inexistente",
        "/clientes?rfc=inexistente", "/clientes?correo=inexistente", "/clientes?numeroCuenta=inexistente",
        "/cuentas/inexistente", "/cuentas/inexistente/saldo", "/cuentas/id/2147483647",
        "/cuentas?id=2147483647", "/cuentas?saldo=9999999999999999.99"})
    void consultasSinCoincidenciasDevuelven404(String ruta) throws Exception {
        crear(registro("1"));
        mvc.perform(get(ruta).header("Authorization", bearer("1")))
            .andExpect(status().isNotFound()).andExpect(jsonPath("codigo").value(1));
    }
    @ParameterizedTest @ValueSource(strings={"/clientes?tamanio=101", "/clientes?pagina=-1",
        "/cuentas?saldo=-1", "/cuentas?curp=inexistente", "/cuentas?rfc=inexistente",
        "/cuentas?clienteId=1", "/cuentas?numeroCuenta=1", "/cuentas?activa=true&activo=false"})
    void filtrosInvalidosDevuelven400(String ruta) throws Exception {
        crear(registro("1"));
        mvc.perform(get(ruta).header("Authorization", bearer("1")))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("codigo").value(1));
    }
    @Test void cambioPasswordSoloAfectaAlUsuarioPropioYPermiteLoginConLaNueva() throws Exception {
        JsonNode c = crear(registro("1")); String token = bearer("1"); int uid = c.get("usuario").get("id").asInt();
        mvc.perform(put("/usuarios/" + uid + "/password").header("Authorization", token)
            .contentType("application/json").content("{\"passwordActual\":\"Segura123!\",\"passwordNueva\":\"Nueva123!\"}"))
            .andExpect(status().isNoContent());
        login("ana1@example.com", "Nueva123!");
        mvc.perform(post("/auth/login").contentType("application/json").content(credenciales("ana1@example.com", "Segura123!")))
            .andExpect(status().isUnauthorized());
    }
    @Test void consultaUsuarioPropioNoExponeHashYRechazaUsuarioAjeno() throws Exception {
        JsonNode propio = crear(registro("1")); JsonNode ajeno = crear(registro("2")); String token = bearer("1");
        mvc.perform(get("/usuarios/" + propio.get("usuario").get("id").asInt()).header("Authorization", token))
            .andExpect(status().isOk()).andExpect(jsonPath("correo").value("ana1@example.com"))
            .andExpect(jsonPath("passwordHash").doesNotExist()).andExpect(jsonPath("password").doesNotExist());
        mvc.perform(get("/usuarios/" + ajeno.get("usuario").get("id").asInt()).header("Authorization", token))
            .andExpect(status().isForbidden());
    }
    @ParameterizedTest @ValueSource(strings={"/clientes", "/clientes/1", "/cuentas/1", "/usuarios/1"})
    void endpointsProtegidosRechazanSolicitudesSinJwt(String ruta) throws Exception {
        mvc.perform(get(ruta)).andExpect(status().isUnauthorized()).andExpect(jsonPath("codigo").value(1));
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
    private JsonNode json(MvcResult r) throws Exception { return mapper.readTree(r.getResponse().getContentAsString()); }
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
