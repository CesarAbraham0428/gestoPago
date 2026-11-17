package com.proyecto.servicios.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.proyecto.servicios.service.SesionService;
import com.proyecto.servicios.service.SesionService.Estado;
import java.util.Date;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

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
}
