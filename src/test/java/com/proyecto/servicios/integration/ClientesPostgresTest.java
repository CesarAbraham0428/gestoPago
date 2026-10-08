package com.proyecto.servicios.integration;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.proyecto.servicios.config.*;
import com.proyecto.servicios.controller.*;
import com.proyecto.servicios.controller.advice.ApiExceptionHandler;
import com.proyecto.servicios.model.CatalogoProductosResponse;
import com.proyecto.servicios.security.*;
import com.proyecto.servicios.service.*;
import com.proyecto.servicios.service.Impl.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.*;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.*;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.*;
import javax.sql.DataSource;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Usa exclusivamente una BD aislada cuyo nombre empieza con gestopago_backend_test_. */
@EnabledIfEnvironmentVariable(named="TEST_DB_URL",matches=".*gestopago_backend_test_[a-zA-Z0-9_]+.*")
@SpringBootTest(classes=ClientesPostgresTest.Config.class)
@AutoConfigureMockMvc
class ClientesPostgresTest {
    @SpringBootConfiguration @EnableAutoConfiguration
    @Import({ConfigDB.class,FlywayConfig.class,SecurityConfiguration.class,JwtTokenService.class,
        ClienteController.class,CuentaController.class,UsuarioController.class,AutenticacionController.class,
        ProductoController.class,ApiExceptionHandler.class,ClienteServiceImpl.class,
        CuentaService.class,UsuarioService.class,AutenticacionServiceImpl.class})
    static class Config {}
    @DynamicPropertySource static void propiedades(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url",() -> System.getenv("TEST_DB_URL"));
        r.add("spring.datasource.username",() -> System.getenv("TEST_DB_USERNAME"));
        r.add("spring.datasource.password",() -> System.getenv("TEST_DB_PASSWORD"));
        r.add("gestopago.app.jwt.secret",() -> "test-only-secret-012345678901234567890123456789");
    }
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired DataSource datasource;
    @SpyBean PasswordEncoder encoder;
    @MockBean ProductoService productos;
    private ObjectNode registro(String sufijo) throws Exception {
        return ((ObjectNode)mapper.readTree("""
            {"primerNombre":"Ana","segundoNombre":null,"apellidoPaterno":"Perez","apellidoMaterno":"Ruiz",
             "fechaNacimiento":"1990-01-01","curp":"PERA900101MDFRZN00","rfc":"PERA900101AB0",
             "sexo":"Femenino","nacionalidad":"Mexicana","estadoCivil":"Union libre",
             "correoElectronico":"ana0@example.com","telefonoMovil":"5512345678","telefonoAlternativo":null,
             "ocupacion":"Ingeniera","empresa":"Empresa","ingresoMensual":12000.50,
             "domicilio":{"calle":"Reforma","numeroExterior":"10","numeroInterior":null,"colonia":"Centro",
             "municipio":"Cuauhtemoc","estado":"Ciudad de Mexico","codigoPostal":"06000","pais":"Mexico"},
             "password":"Segura123!"}
            """)).put("curp","PERA900101MDFRZN0"+sufijo).put("rfc","PERA900101AB"+sufijo)
            .put("correoElectronico","ana"+sufijo+"@example.com");
    }
    private JsonNode json(MvcResult r) throws Exception { return mapper.readTree(r.getResponse().getContentAsString()); }
    private String login(String correo,String password) throws Exception {
        return json(mvc.perform(post("/auth/login").contentType("application/json")
            .content(mapper.createObjectNode().put("correo",correo).put("password",password).toString()))
            .andExpect(status().isOk()).andReturn()).get("token").asText();
    }
    @Test void flujoCompletoRespetaTriggersSeguridadYProductos() throws Exception {
        JdbcTemplate jdbc=new JdbcTemplate(datasource);
        assertTrue(jdbc.queryForObject("select current_database()",String.class).startsWith("gestopago_backend_test_"));
        ObjectNode body=registro("1");
        JsonNode c=json(mvc.perform(post("/clientes").contentType("application/json").content(body.toString()))
            .andExpect(status().isCreated()).andReturn());
        int id=c.get("id").asInt(),uid=c.get("usuario").get("id").asInt();
        String numero=c.get("cuentas").get(0).get("numeroCuenta").asText();
        assertEquals(18,numero.length()); assertEquals("Union libre",c.get("estadoCivil").asText());
        assertFalse(c.get("fechaCreacion").isNull()); assertFalse(c.toString().contains("password"));
        assertTrue(encoder.matches("Segura123!",jdbc.queryForObject("select password_hash from usuarios where id=?",String.class,uid)));
        mvc.perform(post("/clientes").contentType("application/json").content(body.toString())).andExpect(status().isConflict());
        String bearer="Bearer "+login("ANA1@example.com","Segura123!");
        mvc.perform(get("/clientes")).andExpect(status().isUnauthorized());
        mvc.perform(get("/clientes").param("curp",body.get("curp").asText()).header("Authorization",bearer))
            .andExpect(status().isOk()).andExpect(jsonPath("totalElements").value(1));
        mvc.perform(get("/clientes").param("numeroCuenta",numero).header("Authorization",bearer))
            .andExpect(status().isOk()).andExpect(jsonPath("content[0].id").value(id));
        mvc.perform(get("/cuentas/"+numero+"/saldo").header("Authorization",bearer)).andExpect(status().isOk());
        mvc.perform(get("/usuarios/"+uid).header("Authorization",bearer)).andExpect(status().isOk())
            .andExpect(jsonPath("passwordHash").doesNotExist());
        ObjectNode update=body.deepCopy(); update.remove("password");
        mvc.perform(put("/clientes/"+id).header("Authorization",bearer).contentType("application/json").content(update.toString()))
            .andExpect(status().isBadRequest());
        update.remove("curp"); update.remove("rfc"); update.put("correoElectronico","nueva@example.com");
        mvc.perform(put("/clientes/"+id).header("Authorization",bearer).contentType("application/json").content(update.toString()))
            .andExpect(status().isOk()).andExpect(jsonPath("usuario.correo").value("nueva@example.com"));
        assertEquals("nueva@example.com",jdbc.queryForObject("select correo from usuarios where id=?",String.class,uid));
        mvc.perform(get("/usuarios/"+uid).header("Authorization",bearer)).andExpect(status().isOk());
        mvc.perform(put("/usuarios/"+uid+"/password").header("Authorization",bearer).contentType("application/json")
            .content("{\"passwordActual\":\"Segura123!\",\"passwordNueva\":\"Nueva123!\"}"))
            .andExpect(status().isNoContent());
        bearer="Bearer "+login("nueva@example.com","Nueva123!");
        CatalogoProductosResponse catalogo=new CatalogoProductosResponse(); catalogo.setCodigo(0);
        when(productos.obtenerProductos()).thenReturn(catalogo);
        mvc.perform(get("/productos").header("Authorization",bearer)).andExpect(status().isOk());
        verify(productos).obtenerProductos();
        mvc.perform(delete("/cuentas/"+numero).header("Authorization",bearer)).andExpect(status().isNoContent());
        assertFalse(jdbc.queryForObject("select esta_activa from cuentas where numero_cuenta=?",Boolean.class,numero));
        mvc.perform(put("/cuentas/"+numero+"/estado").header("Authorization",bearer).contentType("application/json")
            .content("{\"activa\":true}")).andExpect(status().isOk());
        mvc.perform(delete("/clientes/"+id).header("Authorization",bearer)).andExpect(status().isNoContent());
        assertFalse(jdbc.queryForObject("select activo from usuarios where id=?",Boolean.class,uid));
        assertFalse(jdbc.queryForObject("select esta_activa from cuentas where numero_cuenta=?",Boolean.class,numero));
        mvc.perform(get("/productos").header("Authorization",bearer)).andExpect(status().isUnauthorized());
        mvc.perform(post("/auth/login").contentType("application/json")
            .content("{\"correo\":\"nueva@example.com\",\"password\":\"Nueva123!\"}"))
            .andExpect(status().isForbidden());
    }
    @Test void falloDeUsuarioRevierteTodoElRegistro() throws Exception {
        ObjectNode body=registro("2");
        doReturn("hash-invalido").when(encoder).encode(anyString());
        try {
            mvc.perform(post("/clientes").contentType("application/json").content(body.toString())).andExpect(status().isBadRequest());
            JdbcTemplate jdbc=new JdbcTemplate(datasource);
            assertEquals(0,jdbc.queryForObject("select count(*) from clientes where curp=?",Integer.class,body.get("curp").asText()));
            assertEquals(0,jdbc.queryForObject("select count(*) from usuarios where correo=?",Integer.class,body.get("correoElectronico").asText()));
        } finally { reset(encoder); }
    }
    @Test void rechazaEdadPasswordYTelefonoInvalidos() throws Exception {
        ObjectNode body=registro("3");
        body.put("fechaNacimiento",java.time.LocalDate.now().minusYears(17).toString());
        mvc.perform(post("/clientes").contentType("application/json").content(body.toString())).andExpect(status().isBadRequest());
        body.put("fechaNacimiento","1990-01-01").put("password","abcdefgh");
        mvc.perform(post("/clientes").contentType("application/json").content(body.toString())).andExpect(status().isBadRequest());
        body.put("password","Segura123!").put("telefonoMovil","123");
        mvc.perform(post("/clientes").contentType("application/json").content(body.toString())).andExpect(status().isBadRequest());
    }
    @Test void consultasDuplicadosYRestriccionesDeBaseDeDatos() throws Exception {
        ObjectNode body = registro("4").put("nacionalidad", "Argentina");
        // Cumplir 18 años exactamente permite el registro.
        body.put("fechaNacimiento", java.time.LocalDate.now(java.time.ZoneId.of("America/Mexico_City"))
            .minusYears(18).toString());
        JsonNode c = json(mvc.perform(post("/clientes").contentType("application/json").content(body.toString()))
            .andExpect(status().isCreated()).andExpect(jsonPath("nacionalidad").value("Argentina")).andReturn());
        int id = c.get("id").asInt();
        String numero = c.get("cuentas").get(0).get("numeroCuenta").asText();
        String bearer = "Bearer " + login("ana4@example.com", "Segura123!");
        assertEquals(0, c.get("cuentas").get(0).get("saldo").decimalValue().signum());
        assertTrue(c.get("cuentas").get(0).get("estaActiva").asBoolean());
        for (String campo : new String[]{"curp", "rfc", "correoElectronico"}) {
            ObjectNode duplicado = registro("5");
            duplicado.set(campo, body.get(campo));
            mvc.perform(post("/clientes").contentType("application/json").content(duplicado.toString()))
                .andExpect(status().isConflict());
        }
        for (String filtro : new String[]{"curp", "rfc", "correo"}) {
            String valor = body.get(filtro.equals("correo") ? "correoElectronico" : filtro).asText();
            mvc.perform(get("/clientes").param(filtro, valor).header("Authorization", bearer))
                .andExpect(status().isOk()).andExpect(jsonPath("totalElements").value(1))
                .andExpect(jsonPath("content[0].id").value(id));
        }
        String hoy = java.time.LocalDate.now(java.time.ZoneId.of("America/Mexico_City")).toString();
        mvc.perform(get("/clientes").param("desde", hoy).param("hasta", hoy).param("activo", "true")
            .param("curp", body.get("curp").asText()).header("Authorization", bearer))
            .andExpect(status().isOk()).andExpect(jsonPath("totalElements").value(1));
        mvc.perform(get("/clientes").param("desde", "2026-10-05").param("hasta", "2020-01-01")
            .header("Authorization", bearer)).andExpect(status().isBadRequest());
        mvc.perform(get("/clientes").param("tamanio", "101").header("Authorization", bearer))
            .andExpect(status().isBadRequest());
        mvc.perform(get("/clientes/2147483647").header("Authorization", bearer)).andExpect(status().isNotFound());
        mvc.perform(get("/cuentas/inexistente").header("Authorization", bearer)).andExpect(status().isNotFound());
        mvc.perform(get("/cuentas").param("activa", "true").header("Authorization", bearer))
            .andExpect(status().isOk());
        mvc.perform(get("/usuarios/2147483647").header("Authorization", bearer)).andExpect(status().isForbidden());
        mvc.perform(put("/usuarios/" + c.get("usuario").get("id").asInt() + "/password")
            .header("Authorization", bearer).contentType("application/json")
            .content("{\"passwordActual\":\"Incorrecta1!\",\"passwordNueva\":\"Nueva123!\"}"))
            .andExpect(status().isUnauthorized());

        JdbcTemplate jdbc = new JdbcTemplate(datasource);
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class,
            () -> jdbc.update("update clientes set primer_nombre='Ana1' where id=?", id));
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class,
            () -> jdbc.update("update cuentas set saldo=-1 where numero_cuenta=?", numero));
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class,
            () -> jdbc.update("update cuentas set numero_cuenta='999999999999999999' where numero_cuenta=?", numero));
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class,
            () -> jdbc.update("delete from cuentas where numero_cuenta=?", numero));
        // La baja deja los registros y el esquema impide reactivar una cuenta de ese cliente.
        mvc.perform(delete("/clientes/" + id).header("Authorization", bearer)).andExpect(status().isNoContent());
        assertEquals(1, jdbc.queryForObject("select count(*) from clientes where id=?", Integer.class, id));
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class,
            () -> jdbc.update("update cuentas set esta_activa=true where numero_cuenta=?", numero));
    }
}
