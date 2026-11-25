package com.proyecto.servicios.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.proyecto.servicios.client.GestoPagoPagoException;
import com.proyecto.servicios.client.GestoPagoPagosClient;
import com.proyecto.servicios.config.*;
import com.proyecto.servicios.controller.*;
import com.proyecto.servicios.controller.advice.GlobalExceptionHandler;
import com.proyecto.servicios.entity.gestopago.GestoPagoToken;
import com.proyecto.servicios.models.response.gestopago.GestoPagoPagosResponse;
import com.proyecto.servicios.security.*;
import com.proyecto.servicios.service.*;
import com.proyecto.servicios.service.Impl.*;
import com.proyecto.servicios.utils.CifradoTokenConverter;
import com.proyecto.servicios.utils.FirmaGestoPago;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import com.proyecto.servicios.utils.LimitadorVentanaFija;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Flujo de pago contra PostgreSQL real (BD temporal de backend/scripts/test-postgres.ps1); GestoPago se simula.
 * Comprueba lo que solo se ve con commits reales: saldo retenido/devuelto, estados y el contador de códigos OTP incorrectos.
 */
@EnabledIfEnvironmentVariable(named="TEST_DB_URL", matches=".*gestopago_backend_test_[a-zA-Z0-9_]+.*")
@SpringBootTest(classes=PagosPostgresTest.Config.class)
@AutoConfigureMockMvc
class PagosPostgresTest {
    @SpringBootConfiguration @EnableAutoConfiguration
    @Import({ConfigDB.class, FlywayConfig.class, SecurityConfiguration.class, JwtTokenService.class, ApiSecurityErrorWriter.class, LimitadorVentanaFija.class,
        ClienteController.class, CuentaController.class, UsuarioController.class, AutenticacionController.class,
        PagoController.class, NotificacionController.class, GlobalExceptionHandler.class,
        ClienteServiceImpl.class, CuentaService.class, AutorizacionService.class, UsuarioService.class,
        AutenticacionServiceImpl.class, PagoServiceImpl.class, PagoRegistroService.class, OtpPagoService.class, NotificacionService.class,
        GestoPagoPagosGateway.class, ResolutorDestinoPago.class, VerificadorAdeudo.class, ConciliacionPagoService.class,
        FirmaGestoPago.class, CifradoTokenConverter.class,
        CodigoVerificacionService.class, SesionService.class, NotificadorSesiones.class, TotpService.class, RelojDePrueba.class,
        CodigosDePrueba.class})
    static class Config {}

    @DynamicPropertySource static void propiedades(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", () -> System.getenv("TEST_DB_URL"));
        r.add("spring.datasource.username", () -> System.getenv("TEST_DB_USERNAME"));
        r.add("spring.datasource.password", () -> System.getenv("TEST_DB_PASSWORD"));
        r.add("gestopago.app.jwt.secret", () -> "test-only-secret-012345678901234567890123456789");
        // Estas pruebas hacen cientos de llamadas desde una sola IP: los límites globales no son lo que se prueba aquí.
        r.add("gestopago.app.limites.global-por-ip", () -> "1000000");
        r.add("gestopago.app.limites.global-por-usuario", () -> "1000000");
        r.add("gestopago.app.limites.rafaga-publica-por-ip", () -> "1000000");
        r.add("gestopago.auth.token-key", () -> "HPo7OLqB4Fkk4E2yGOtwqw8H5fHR9kNx67OR4g4UdlA=");
        r.add("gestopago.pagos.secret_key", () -> "HPo7OLqB4Fkk4E2yGOtwqw8H5fHR9kNx67OR4g4UdlA=");
        r.add("gestopago.pagos.iv", () -> "p5ldmBPdd/9pjC0bDC/nSg==");
        r.add("gestopago.auth.id-distribuidor", () -> "10");
        r.add("gestopago.auth.codigo-dispositivo", () -> "DISP");
    }
    private static final String CLIENTE = """
            {"primerNombre":"Ana","apellidoPaterno":"Perez","apellidoMaterno":"Ruiz",
             "fechaNacimiento":"1990-01-01","curp":"PERA900101MDFRZN01","rfc":"PERA900101AB1",
             "sexo":"Femenino","nacionalidad":"Argentina","estadoCivil":"Union libre",
             "correoElectronico":"ana1@example.com","telefonoMovil":"5512345678",
             "ocupacion":"Ingeniera","empresa":"Empresa","ingresoMensual":12000.50,
             "domicilio":{"calle":"Reforma","numeroExterior":"10","colonia":"Centro",
             "municipio":"Cuauhtemoc","estado":"Ciudad de Mexico","codigoPostal":"06000","pais":"Mexico"},
             "password":"Segura123!"}
            """;

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired DataSource datasource;
    @Autowired ConciliacionPagoService conciliacion;
    @MockBean GestoPagoPagosClient gestoPago;
    @MockBean GestoPagoTokenService tokens;
    @MockBean LimitePagosService limite;
    @MockBean LimiteAutenticacionService limiteAutenticacion;
    @Autowired CodigosDePrueba codigos;
    private JdbcTemplate jdbc;
    private String token;
    private String numeroCuenta;

    @BeforeEach void preparar() throws Exception {
        jdbc = new JdbcTemplate(datasource);
        String nombre = jdbc.queryForObject("select current_database()", String.class);
        assertTrue(nombre != null && nombre.matches("gestopago_backend_test_[a-f0-9]{32}"),
            "La limpieza solo está permitida en una BD temporal de pruebas");
        jdbc.execute("TRUNCATE TABLE notificaciones, pagos, usuarios, cuentas, domicilios, clientes, producto RESTART IDENTITY CASCADE");
        jdbc.update("insert into producto (producto, servicio, id_servicio, id_producto, id_cat_tipo_servicio, tipo_front, precio)"
            + " values ('Telcel $100', 'Telcel', 30, 20, 2, 1, 100.00), ('Luz', 'CFE', 31, 21, 5, 2, 12.00)");
        GestoPagoToken gp = new GestoPagoToken();
        gp.setToken("tok-1");
        when(tokens.obtenerTokenActivo(10, "DISP")).thenReturn(Optional.of(gp));

        numeroCuenta = registrarCliente().get("cuentas").get(0).get("numeroCuenta").asText();
        token = "Bearer " + iniciarSesion("ana1@example.com");
        jdbc.update("update cuentas set saldo = 500 where numero_cuenta = ?", numeroCuenta);
    }

    @Test void recargaAprobadaDescuentaSaldoYGuardaLaEvidencia() throws Exception {
        when(gestoPago.enviar(eq("tok-1"), anyString())).thenReturn(respuesta("01", "<RESPONSE>01</RESPONSE>"));

        pagar(recarga(codigoNuevo())).andExpect(status().isOk())
            .andExpect(jsonPath("datos.estado").value("APROBADO"))
            .andExpect(jsonPath("datos.saldoDisponible").value(400.00));

        assertSaldo("400.00");
        assertEquals("APROBADO", jdbc.queryForObject("select estado from pagos", String.class));
        assertEquals("<RESPONSE>01</RESPONSE>", jdbc.queryForObject("select respuesta from pagos", String.class));
        assertEquals(List.of("PAGO_APROBADO"), tiposDeNotificacion());
    }

    @Test void lasNotificacionesDelPagoSoloLasVeSuDuenoYSePuedenMarcarComoLeidas() throws Exception {
        when(gestoPago.enviar(any(), any())).thenReturn(respuesta("01", "<RESPONSE>01</RESPONSE>"));
        pagar(recarga(codigoNuevo())).andExpect(status().isOk());

        consultarNotificaciones(token).andExpect(status().isOk())
            .andExpect(jsonPath("datos.noLeidas").value(1))
            .andExpect(jsonPath("datos.lista.datos[0].tipo").value("PAGO_APROBADO"))
            .andExpect(jsonPath("datos.lista.datos[0].mensaje").value(org.hamcrest.Matchers.containsString("••••5678")));

        String otroToken = "Bearer " + registrarOtroClienteYIniciarSesion();
        consultarNotificaciones(otroToken).andExpect(status().isOk())
            .andExpect(jsonPath("datos.noLeidas").value(0));
        Integer id = jdbc.queryForObject("select id from notificaciones", Integer.class);
        mvc.perform(post("/notificaciones/leer").header("Authorization", otroToken).contentType("application/json")
            .content("{\"id\":" + id + "}")).andExpect(status().isOk());
        assertEquals(1, jdbc.queryForObject("select count(*) from notificaciones where not leida", Integer.class));

        mvc.perform(post("/notificaciones/leer-todas").header("Authorization", token)).andExpect(status().isOk());
        consultarNotificaciones(token).andExpect(jsonPath("datos.noLeidas").value(0));
        mvc.perform(post("/notificaciones/consultar").contentType("application/json").content("{}"))
            .andExpect(status().isUnauthorized());
    }

    @Test void pagoDeServicioCobraMontoMasComision() throws Exception {
        when(gestoPago.enviar(any(), any())).thenReturn(respuesta("01", "<RESPONSE>01</RESPONSE>"));

        pagar("{\"numeroCuenta\":\"" + numeroCuenta + "\",\"idProducto\":21,\"idServicio\":31,\"monto\":100.00,"
            + "\"referencia\":\"REF123\",\"codigoOtp\":\"" + codigoNuevo() + "\"}")
            .andExpect(status().isOk()).andExpect(jsonPath("datos.comision").value(12.00));

        assertSaldo("388.00");
    }

    @Test void rechazoDelProveedorDevuelveElSaldoSinExponerSuMensaje() throws Exception {
        GestoPagoPagosResponse rechazo = respuesta("15", "<RESPONSE>15</RESPONSE>");
        rechazo.setMensaje("Detalle interno del proveedor");
        when(gestoPago.enviar(any(), any())).thenReturn(rechazo);

        pagar(recarga(codigoNuevo())).andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("mensaje").value("El proveedor rechazó el pago; no se realizó ningún cargo"));

        assertSaldo("500.00");
        assertEquals("Detalle interno del proveedor", jdbc.queryForObject("select mensaje from pagos", String.class));
        assertEquals(List.of("PAGO_RECHAZADO"), tiposDeNotificacion());
    }

    @Test void timeoutRetieneElSaldoHastaQueConfirmTxDiceNoAplicada() throws Exception {
        when(gestoPago.enviar(any(), any())).thenThrow(new GestoPagoPagoException("timeout",
            GestoPagoPagoException.Tipo.TIMEOUT, null, true, null));

        pagar(recarga(codigoNuevo())).andExpect(status().isGatewayTimeout());
        assertSaldo("400.00");
        assertEquals("EN_REVISION", jdbc.queryForObject("select estado from pagos", String.class));

        envejecerPagos();
        when(gestoPago.confirmar(eq("tok-1"), anyString())).thenReturn(respuesta("70", "<RESPONSE>70</RESPONSE>"));
        conciliacion.conciliarPendientes();

        assertSaldo("500.00");
        assertEquals("RECHAZADO", jdbc.queryForObject("select estado from pagos", String.class));
        assertEquals(List.of("PAGO_EN_REVISION", "PAGO_RECHAZADO"), tiposDeNotificacion());
    }

    @Test void cincoCodigosIncorrectosBloqueanLosPagosAunqueLaTransaccionFalle() throws Exception {
        for (int i = 0; i < 4; i++) {
            pagar(recarga("000000")).andExpect(status().isForbidden());
        }
        assertEquals(4, jdbc.queryForObject("select intentos_pin_fallidos from cuentas", Integer.class));

        pagar(recarga("000000")).andExpect(status().isForbidden());
        pagar(recarga(codigoNuevo())).andExpect(status().isLocked());

        assertSaldo("500.00");
        assertEquals(0, jdbc.queryForObject("select count(*) from pagos", Integer.class));
        verifyNoInteractions(gestoPago);
    }

    @Test void pagosSimultaneosNoUsanDosVecesElMismoCodigo() throws Exception {
        String codigo = codigoNuevo();
        when(gestoPago.enviar(any(), any())).thenReturn(respuesta("01", "<RESPONSE>01</RESPONSE>"));
        int solicitudes = 8;
        ExecutorService hilos = Executors.newFixedThreadPool(solicitudes);
        CountDownLatch salida = new CountDownLatch(1);
        try {
            List<Future<Integer>> resultados = new ArrayList<>();
            for (int i = 0; i < solicitudes; i++) {
                resultados.add(hilos.submit(() -> {
                    salida.await();
                    return pagar(recarga(codigo)).andReturn().getResponse().getStatus();
                }));
            }
            salida.countDown();
            int aprobados = 0;
            int rechazados = 0;
            for (Future<Integer> resultado : resultados) {
                int estado = resultado.get(60, TimeUnit.SECONDS);
                if (estado == 200) aprobados++;
                // 403: código ya usado; 423: tras 5 fallos la cuenta queda bloqueada.
                else if (estado == 403 || estado == 423) rechazados++;
            }
            assertEquals(1, aprobados, "Un mismo código OTP solo autoriza un pago");
            assertEquals(solicitudes - 1, rechazados);
        } finally {
            hilos.shutdownNow();
        }

        assertSaldo("400.00");
        assertEquals(1, jdbc.queryForObject("select count(*) from pagos", Integer.class));
        verify(gestoPago, times(1)).enviar(any(), any());
    }

    @Test void consultaDevuelveElEstadoSoloAlDuenoDelPago() throws Exception {
        when(gestoPago.enviar(any(), any())).thenReturn(respuesta("01", "<RESPONSE>01</RESPONSE>"));
        pagar(recarga(codigoNuevo())).andExpect(status().isOk());
        String upc = jdbc.queryForObject("select upc from pagos", String.class);

        mvc.perform(post("/pagos/consulta").header("Authorization", token).contentType("application/json")
                .content("{\"upc\":\"" + upc + "\"}")).andExpect(status().isOk())
            .andExpect(jsonPath("datos.estado").value("APROBADO"))
            .andExpect(jsonPath("datos.numAutorizacion").value("100024988"));

        String otroToken = "Bearer " + registrarOtroClienteYIniciarSesion();
        mvc.perform(post("/pagos/consulta").header("Authorization", otroToken).contentType("application/json")
                .content("{\"upc\":\"" + upc + "\"}")).andExpect(status().isNotFound());
        mvc.perform(post("/pagos/consulta").contentType("application/json")
                .content("{\"upc\":\"" + upc + "\"}")).andExpect(status().isUnauthorized());
    }

    private ResultActions consultarNotificaciones(String bearer) throws Exception {
        return mvc.perform(post("/notificaciones/consultar").header("Authorization", bearer)
            .contentType("application/json").content("{}"));
    }

    private List<String> tiposDeNotificacion() {
        return jdbc.queryForList("select tipo from notificaciones order by id", String.class);
    }

    private ResultActions pagar(String cuerpo) throws Exception {
        return mvc.perform(post("/pagos").header("Authorization", token).contentType("application/json").content(cuerpo));
    }

    private String recarga(String codigo) {
        return "{\"numeroCuenta\":\"" + numeroCuenta + "\",\"idProducto\":20,\"idServicio\":30,\"monto\":100.00,"
            + "\"telefono\":\"5512345678\",\"codigoOtp\":\"" + codigo + "\"}";
    }

    private String codigoNuevo() {
        return codigos.nuevoCodigo("ana1@example.com");
    }

    private void assertSaldo(String esperado) {
        assertEquals(new BigDecimal(esperado),
            jdbc.queryForObject("select saldo from cuentas where numero_cuenta = ?", BigDecimal.class, numeroCuenta));
    }

    // confirmTx solo se consulta 62 s después de sendTx; el trigger de auditoría impide cambiar fecha_creacion.
    private void envejecerPagos() {
        jdbc.execute("ALTER TABLE pagos DISABLE TRIGGER trg_auditoria_pagos");
        try {
            jdbc.update("update pagos set fecha_creacion = fecha_creacion - interval '2 minutes'");
        } finally {
            jdbc.execute("ALTER TABLE pagos ENABLE TRIGGER trg_auditoria_pagos");
        }
    }

    private GestoPagoPagosResponse respuesta(String codigo, String original) {
        GestoPagoPagosResponse respuesta = new GestoPagoPagosResponse();
        respuesta.setCodigo(codigo);
        respuesta.setIdTx(900L);
        respuesta.setNumAutorizacion("100024988");
        respuesta.setRespuestaOriginal(original);
        return respuesta;
    }

    private String registrarOtroClienteYIniciarSesion() throws Exception {
        mvc.perform(post("/clientes").contentType("application/json").content(CLIENTE
            .replace("PERA900101MDFRZN01", "PERA900102MDFRZN02").replace("PERA900101AB1", "PERA900102AB2")
            .replace("ana1@example.com", "ana2@example.com")
            .replace("\"telefonoMovil\":\"5512345678\"", "\"telefonoMovil\":\"5512345679\""))).andExpect(status().isCreated());
        return iniciarSesion("ana2@example.com");
    }

    /** Inicio de sesión completo en dos pasos; devuelve el JWT. */
    private String iniciarSesion(String usuario) throws Exception {
        String desafio = datos(mvc.perform(post("/auth/login").contentType("application/json")
            .content("{\"correo\":\"" + usuario + "\",\"password\":\"Segura123!\"}"))
            .andExpect(status().isOk())).get("desafioId").asText();
        return datos(mvc.perform(post("/auth/verificar").contentType("application/json")
            .content("{\"desafioId\":\"" + desafio + "\",\"codigo\":\"" + codigos.nuevoCodigo(usuario)
                + "\",\"cerrarSesionAnterior\":true}")).andExpect(status().isOk())).get("token").asText();
    }

    private JsonNode registrarCliente() throws Exception {
        return datos(mvc.perform(post("/clientes").contentType("application/json").content(CLIENTE)).andExpect(status().isCreated()));
    }

    private JsonNode datos(ResultActions resultado) throws Exception {
        return mapper.readTree(resultado.andReturn().getResponse().getContentAsString()).get("datos");
    }
}
