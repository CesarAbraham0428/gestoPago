package com.proyecto.servicios.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.proyecto.servicios.service.SesionService;
import com.proyecto.servicios.service.SesionService.Estado;
import com.proyecto.servicios.utils.LimitadorVentanaFija;
import com.proyecto.servicios.utils.LimitadorVentanaFija.Resultado;
import java.time.Duration;
import java.util.Date;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** Cadena de filtros real (rutas públicas, JWT, CORS y errores JSON) sobre controladores de prueba. */
@WebMvcTest
@ContextConfiguration(classes = {SecurityConfigurationTest.Config.class, SecurityConfiguration.class,
        ApiSecurityErrorWriter.class})
class SecurityConfigurationTest {

    private static final String SECRETO = "una-llave-de-prueba-de-al-menos-32-bytes!!";

    @Import(PruebaController.class)
    @EnableMethodSecurity
    static class Config {
    }

    @RestController
    static class PruebaController {
        @PostMapping("/auth/login")
        String login() {
            return "ok";
        }

        @GetMapping("/protegida")
        String protegida() {
            return "dentro";
        }

        /** Canal SSE que se cierra enseguida: obliga a un despacho ASYNC por la cadena de seguridad. */
        @GetMapping("/eventos")
        SseEmitter eventos() throws java.io.IOException {
            SseEmitter emisor = new SseEmitter();
            emisor.send(SseEmitter.event().name("conectado").data("ok"));
            emisor.complete();
            return emisor;
        }

        @PreAuthorize("hasRole('ADMIN')")
        @GetMapping("/solo-admin")
        String soloAdmin() {
            return "admin";
        }
    }

    @Autowired
    private MockMvc mvc;
    @Autowired
    private PasswordEncoder encoder;
    @MockBean
    private JwtTokenService tokens;
    @MockBean
    private SesionService sesiones;
    @MockBean
    private LimitadorVentanaFija limitador;

    @BeforeEach
    void limitesSinExceso() {
        when(limitador.registrar(any(), anyInt(), any(Duration.class))).thenReturn(Resultado.PERMITIDO);
    }

    private String tokenValido() {
        DecodedJWT jwt = JWT.decode(JWT.create().withSubject("3").withJWTId("jti")
                .withExpiresAt(new Date(System.currentTimeMillis() + 60_000)).sign(Algorithm.HMAC256(SECRETO)));
        when(tokens.validar("bueno")).thenReturn(jwt);
        return "Bearer bueno";
    }

    @Test
    void lasRutasPublicasNoPidenSesion() throws Exception {
        mvc.perform(post("/auth/login")).andExpect(status().isOk());
    }

    @Test
    void unaRutaProtegidaSinTokenResponde401EnJson() throws Exception {
        mvc.perform(get("/protegida"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.mensaje").value("Se requiere iniciar sesión"));
    }

    @Test
    void unEncabezadoQueNoEsBearerSeIgnora() throws Exception {
        mvc.perform(get("/protegida").header(HttpHeaders.AUTHORIZATION, "Basic abc"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void conUnTokenYSesionVigentesPasa() throws Exception {
        when(sesiones.estado(eq(3), eq("jti"))).thenReturn(Estado.VALIDA);

        mvc.perform(get("/protegida").header(HttpHeaders.AUTHORIZATION, tokenValido()))
                .andExpect(status().isOk());
    }

    @Test
    void unCanalSseAutenticadoTerminaSinQueLaSeguridadBloqueeElDespachoAsincrono() throws Exception {
        when(sesiones.estado(eq(3), eq("jti"))).thenReturn(Estado.VALIDA);

        MvcResult inicio = mvc.perform(get("/eventos").header(HttpHeaders.AUTHORIZATION, tokenValido()))
                .andExpect(request().asyncStarted()).andReturn();

        mvc.perform(asyncDispatch(inicio))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("event:conectado")));
    }

    @Test
    void unCanalSseSinTokenResponde401() throws Exception {
        mvc.perform(get("/eventos")).andExpect(status().isUnauthorized());
    }

    @Test
    void sinPermisoDeAdministradorResponde403() throws Exception {
        when(sesiones.estado(eq(3), eq("jti"))).thenReturn(Estado.VALIDA);

        mvc.perform(get("/solo-admin").header(HttpHeaders.AUTHORIZATION, tokenValido()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.mensaje").value("No tienes permiso para realizar esta operación"));
    }

    @Test
    void laPreflightDeUnOrigenPermitidoSeAcepta() throws Exception {
        mvc.perform(options("/protegida")
                        .header(HttpHeaders.ORIGIN, "http://localhost:4200")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:4200"));
    }

    @Test
    void elCodificadorDeContrasenasUsaBcrypt() {
        String hash = encoder.encode("secreto");

        assertTrue(hash.startsWith("$2"));
        assertTrue(encoder.matches("secreto", hash));
        assertEquals(false, encoder.matches("otro", hash));
    }

    @Test
    void soloLosTresPostPublicosSonRutasPublicas() {
        assertTrue(SecurityConfiguration.esRutaPublica("POST", "/clientes"));
        assertEquals(false, SecurityConfiguration.esRutaPublica("GET", "/clientes"));
        assertEquals(false, SecurityConfiguration.esRutaPublica("POST", "/cuentas"));
    }

    @Test
    void superarElLimiteGeneralPorIpResponde429ConRetryAfterYCabecerasCors() throws Exception {
        when(limitador.registrar(startsWith("limite:global:ip:"), anyInt(), any(Duration.class)))
                .thenReturn(new Resultado(true, 42));

        mvc.perform(get("/protegida").header(HttpHeaders.ORIGIN, "http://localhost:4200"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string(HttpHeaders.RETRY_AFTER, "42"))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:4200"))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS, "Retry-After"))
                .andExpect(jsonPath("$.mensaje").exists());
    }

    @Test
    void elLoginTieneUnLimiteDeRafagaMasEstrictoPorIp() throws Exception {
        when(limitador.registrar(startsWith("limite:rafaga:ip:"), anyInt(), any(Duration.class)))
                .thenReturn(new Resultado(true, 30));

        mvc.perform(post("/auth/login")).andExpect(status().isTooManyRequests())
                .andExpect(header().string(HttpHeaders.RETRY_AFTER, "30"));
        // Una ruta protegida no consume la ráfaga pública.
        mvc.perform(get("/protegida")).andExpect(status().isUnauthorized());
    }

    @Test
    void unUsuarioAutenticadoTieneSuPropioLimiteGeneral() throws Exception {
        when(sesiones.estado(eq(3), eq("jti"))).thenReturn(Estado.VALIDA);
        when(limitador.registrar(eq("limite:global:usuario:3"), anyInt(), any(Duration.class)))
                .thenReturn(new Resultado(true, 9));

        mvc.perform(get("/protegida").header(HttpHeaders.AUTHORIZATION, tokenValido()))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string(HttpHeaders.RETRY_AFTER, "9"));
    }

    @Test
    void laSolicitudAnonimaNoCuentaEnElLimiteDeUsuario() throws Exception {
        mvc.perform(get("/protegida")).andExpect(status().isUnauthorized());

        verify(limitador, never()).registrar(startsWith("limite:global:usuario:"), anyInt(), any(Duration.class));
    }

    @Test
    void lasPreflightNoConsumenElLimite() throws Exception {
        mvc.perform(options("/protegida")
                .header(HttpHeaders.ORIGIN, "http://localhost:4200")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")).andExpect(status().isOk());

        verifyNoInteractions(limitador);
    }

    @Test
    void unCuerpoDemasiadoGrandeSeRechazaAntesDeProcesarlo() throws Exception {
        mvc.perform(post("/auth/login").contentType("application/json").content(new byte[70_000]))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.mensaje").value("La solicitud es demasiado grande"));
    }

    @Test
    void laSondaDeSaludEsPublicaYNoConsumeLimite() throws Exception {
        // En esta prueba no hay actuator: lo relevante es que no responde 401 ni 429.
        mvc.perform(get("/actuator/health")).andExpect(status().isNotFound());

        verifyNoInteractions(limitador);
    }

    @Test
    void lasRespuestasLlevanLasCabecerasDeSeguridadDeLaApi() throws Exception {
        mvc.perform(get("/protegida"))
                .andExpect(header().string("Content-Security-Policy",
                        "default-src 'none'; frame-ancestors 'none'; base-uri 'none'"))
                .andExpect(header().string("Permissions-Policy",
                        "geolocation=(), microphone=(), camera=(), payment=(), usb=()"))
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Referrer-Policy", "no-referrer"))
                .andExpect(header().string("Cross-Origin-Opener-Policy", "same-origin"));
    }

    @Test
    void noSePermiteConfigurarCorsParaCualquierOrigen() {
        assertThrows(IllegalStateException.class,
                () -> SecurityConfiguration.corsConfigurationSource(List.of("*")));
        assertThrows(IllegalStateException.class,
                () -> SecurityConfiguration.corsConfigurationSource(List.of("https://app.ejemplo.com", "https://*")));
        assertThrows(IllegalStateException.class,
                () -> SecurityConfiguration.corsConfigurationSource(List.of(" http://* ")));
    }
}
