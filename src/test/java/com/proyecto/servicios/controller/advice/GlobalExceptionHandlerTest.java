package com.proyecto.servicios.controller.advice;

import com.proyecto.servicios.client.GestoPagoCatalogException;
import com.proyecto.servicios.service.exception.ClienteNoEncontradoException;
import com.proyecto.servicios.service.exception.CorreoDuplicadoException;
import com.proyecto.servicios.service.exception.UsuarioNoEncontradoException;
import com.proyecto.servicios.service.exception.NegocioException;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Valid;
import jakarta.validation.Validation;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GlobalExceptionHandlerTest {
    private MockMvc mvc;

    @BeforeEach
    void preparar() {
        mvc = MockMvcBuilders.standaloneSetup(new ControladorPrueba())
                .setControllerAdvice(new GlobalExceptionHandler())
                .alwaysExpect(jsonPath("$").value(org.hamcrest.Matchers.aMapWithSize(2)))
                .alwaysExpect(jsonPath("datos").doesNotHaveJsonPath())
                .alwaysExpect(jsonPath("errores").doesNotHaveJsonPath())
                .build();
    }

    @Test
    void requestInvalidoDevuelveErroresDeCamposSinValoresRechazados() throws Exception {
        mvc.perform(post("/prueba/validar").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"dato-secreto-que-supera-la-longitud\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("codigo").value(1))
                .andExpect(jsonPath("mensaje").value(containsString("nombre: demasiado largo")))
                .andExpect(content().string(not(containsString("dato-secreto"))));
    }

    @Test
    void validacionIdentificaVariosCamposYDomicilioAnidado() throws Exception {
        mvc.perform(post("/prueba/validar").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"\",\"correo\":\"correo-invalido\",\"domicilio\":{\"calle\":\"\"}}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("mensaje").value(containsString("nombre: obligatorio")))
                .andExpect(jsonPath("mensaje").value(containsString("correo: correo inválido")))
                .andExpect(jsonPath("mensaje").value(containsString("domicilio.calle: calle obligatoria")));
    }

    @Test
    void constraintViolationConservaTodosLosCamposSinValoresRechazados() throws Exception {
        mvc.perform(get("/prueba/restricciones"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("mensaje").value(containsString("nombre: demasiado largo")))
                .andExpect(jsonPath("mensaje").value(containsString("correo: correo inválido")))
                .andExpect(content().string(not(containsString("dato-secreto"))));
    }

    @Test
    void usuarioInexistenteDevuelve404() throws Exception {
        mvc.perform(get("/prueba/usuario"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("mensaje").value("Usuario no encontrado"))
                .andExpect(jsonPath("errores").doesNotHaveJsonPath());
    }

    @Test
    void correoDuplicadoDevuelve409() throws Exception {
        mvc.perform(get("/prueba/correo"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("mensaje").value("Correo electrónico duplicado"));
    }

    @ParameterizedTest
    @CsvSource({"23505,409", "23503,409", "23514,400", "23502,400"})
    void restriccionesSqlSeTraducenSinRevelarDetalles(String estado, int http) throws Exception {
        mvc.perform(get("/prueba/sql/" + estado))
                .andExpect(status().is(http))
                .andExpect(jsonPath("codigo").value(1))
                .andExpect(content().string(not(containsString("INSERT"))))
                .andExpect(content().string(not(containsString("usuarios"))))
                .andExpect(content().string(not(containsString(estado))));
    }

    @Test
    void jsonMalformadoDevuelve400SinExponerElContenido() throws Exception {
        mvc.perform(post("/prueba/validar").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"contenido-secreto\""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("codigo").value(1))
                .andExpect(jsonPath("mensaje").value(containsString("Solicitud inválida")))
                .andExpect(content().string(not(containsString("contenido-secreto"))));
    }

    @Test
    void clienteInexistenteConserva404() throws Exception {
        mvc.perform(get("/prueba/inexistente"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("codigo").value(1))
                .andExpect(jsonPath("mensaje").value("Cliente no encontrado"));
    }

    @Test
    void permisoDenegadoConserva403() throws Exception {
        mvc.perform(get("/prueba/prohibido"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("codigo").value(1))
                .andExpect(jsonPath("mensaje").value("Acceso no autorizado"));
    }

    @Test
    void errorInternoNoExponeSqlNiDetalles() throws Exception {
        mvc.perform(get("/prueba/interno"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("codigo").value(1))
                .andExpect(jsonPath("mensaje").value("Ocurrió un error interno en la API"))
                .andExpect(content().string(not(containsString("SELECT"))))
                .andExpect(content().string(not(containsString("password"))))
                .andExpect(content().string(not(containsString("IllegalStateException"))));
    }

    @ParameterizedTest
    @ValueSource(ints = {400, 404, 405, 406, 415, 500, 503})
    void erroresDeSpringConservanSuEstadoSinRevelarSuDetalle(int http) throws Exception {
        mvc.perform(get("/prueba/http/" + http))
                .andExpect(status().is(http))
                .andExpect(jsonPath("codigo").value(1))
                .andExpect(jsonPath("errores").doesNotHaveJsonPath())
                .andExpect(content().string(not(containsString("detalle-secreto"))));
    }

    @ParameterizedTest
    @ValueSource(strings = {"datos", "transaccion", "catalogo"})
    void persistenciaUsaElMismoManejador500SinExponerSql(String tipo) throws Exception {
        mvc.perform(get("/prueba/persistencia/" + tipo))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("mensaje").value("Ocurrió un error interno en la API"))
                .andExpect(content().string(not(containsString("SELECT"))));
    }

    @ParameterizedTest
    @ValueSource(ints = {401, 403})
    void seguridadConservaLaDiferenciaEntreAutenticacionYPermisos(int http) throws Exception {
        mvc.perform(get("/prueba/seguridad/" + http))
                .andExpect(status().is(http))
                .andExpect(jsonPath("codigo").value(1))
                .andExpect(content().string(not(containsString("detalle-secreto"))));
    }

    @Test
    void proveedorExternoNoExponeSuRespuesta() throws Exception {
        mvc.perform(get("/prueba/proveedor"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("codigo").value(1))
                .andExpect(content().string(not(containsString("token-secreto"))));
    }

    @Test
    void restriccionDePersistenciaConserva409SinDetallesSql() throws Exception {
        mvc.perform(get("/prueba/restriccion"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("codigo").value(1))
                .andExpect(content().string(not(containsString("INSERT"))));
    }

    @Test
    void metodoNoPermitidoConserva405YElEncabezadoAllow() throws Exception {
        mvc.perform(put("/prueba/validar").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().string("Allow", containsString("POST")))
                .andExpect(jsonPath("codigo").value(1));
    }

    @Test
    void acceptIncompatibleDevuelve406ConElContratoJsonDeError() throws Exception {
        mvc.perform(get("/prueba/respuesta").accept(MediaType.APPLICATION_XML))
                .andExpect(status().isNotAcceptable())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("codigo").value(1))
                .andExpect(jsonPath("mensaje").value("La API devuelve sus respuestas en formato JSON"))
                .andExpect(jsonPath("datos").doesNotHaveJsonPath())
                .andExpect(jsonPath("errores").doesNotHaveJsonPath());
    }

    @Test
    void errorDeNegocioConAcceptIncompatibleConservaEstadoYContratoJson() throws Exception {
        mvc.perform(get("/prueba/usuario").accept(MediaType.APPLICATION_XML))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("codigo").value(1))
                .andExpect(jsonPath("mensaje").value("Usuario no encontrado"));
    }

    @Test
    void validacionConAcceptIncompatibleConserva400YErroresDeCamposEnJson() throws Exception {
        mvc.perform(post("/prueba/validar").accept(MediaType.APPLICATION_XML)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"\",\"correo\":\"correo-invalido\",\"domicilio\":{\"calle\":\"\"}}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("codigo").value(1))
                .andExpect(jsonPath("mensaje").value(containsString("nombre: obligatorio")))
                .andExpect(jsonPath("mensaje").value(containsString("correo: correo inválido")))
                .andExpect(jsonPath("mensaje").value(containsString("domicilio.calle: calle obligatoria")));
    }

    @Test
    void tipoInvalidoEnRequestDevuelve400SinElValor() throws Exception {
        mvc.perform(post("/prueba/filtros").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pagina\":\"valor-secreto\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("codigo").value(1))
                .andExpect(content().string(not(containsString("valor-secreto"))));
    }

    @Test
    void cuerpoAusenteDevuelve400() throws Exception {
        mvc.perform(post("/prueba/validar").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("codigo").value(1))
                .andExpect(jsonPath("datos").doesNotHaveJsonPath());
    }

    @RestController
    static class ControladorPrueba {
        @GetMapping("/prueba/respuesta")
        com.proyecto.servicios.models.response.GenericResponse<String> respuesta() {
            return com.proyecto.servicios.models.response.GenericResponse.exito("Consulta realizada", "resultado");
        }

        @PostMapping("/prueba/validar")
        void validar(@Valid @RequestBody Solicitud request) {
        }

        @GetMapping("/prueba/inexistente")
        void inexistente() {
            throw new ClienteNoEncontradoException();
        }

        @GetMapping("/prueba/prohibido")
        void prohibido() {
            throw new NegocioException(HttpStatus.FORBIDDEN, "Acceso no autorizado");
        }

        @GetMapping("/prueba/interno")
        void interno() {
            throw new IllegalStateException("SELECT password FROM usuarios WHERE id=77");
        }

        @GetMapping("/prueba/http/{estado}")
        void http(@PathVariable("estado") int estado) {
            throw new ResponseStatusException(HttpStatusCode.valueOf(estado), "detalle-secreto");
        }

        @GetMapping("/prueba/persistencia/{tipo}")
        void persistencia(@PathVariable("tipo") String tipo) {
            throw switch (tipo) {
                case "datos" -> new org.springframework.dao.DataAccessResourceFailureException("SELECT ...");
                case "transaccion" -> new org.springframework.transaction.TransactionSystemException("SELECT ...");
                default -> new com.proyecto.servicios.service.exception.CatalogoPersistenciaException("SELECT ...");
            };
        }

        @GetMapping("/prueba/seguridad/{estado}")
        void seguridad(@PathVariable("estado") int estado) {
            if (estado == 401) {
                throw new org.springframework.security.authentication.AuthenticationCredentialsNotFoundException("detalle-secreto");
            }
            throw new org.springframework.security.access.AccessDeniedException("detalle-secreto");
        }

        @GetMapping("/prueba/proveedor")
        void proveedor() {
            throw new GestoPagoCatalogException("Respuesta remota con token-secreto");
        }

        @GetMapping("/prueba/restriccion")
        void restriccion() {
            throw new DataIntegrityViolationException("INSERT INTO usuarios ...");
        }

        @GetMapping("/prueba/sql/{estado}")
        void sql(@PathVariable("estado") String estado) {
            throw new DataIntegrityViolationException("INSERT INTO usuarios ...",
                    new java.sql.SQLException("detalle-secreto", estado));
        }

        @GetMapping("/prueba/usuario")
        void usuario() {
            throw new UsuarioNoEncontradoException();
        }

        @GetMapping("/prueba/correo")
        void correo() {
            throw new CorreoDuplicadoException();
        }

        @GetMapping("/prueba/restricciones")
        void restricciones() {
            try (var factory = Validation.buildDefaultValidatorFactory()) {
                throw new ConstraintViolationException(factory.getValidator()
                        .validate(new Solicitud("dato-secreto-demasiado-largo", "dato-secreto", null)));
            }
        }

        @PostMapping("/prueba/filtros")
        void filtros(@Valid @RequestBody Filtro request) {
        }
    }

    record Solicitud(
            @NotBlank(message = "obligatorio")
            @Size(max = 10, message = "demasiado largo") String nombre,
            @Email(message = "correo inválido") String correo,
            @Valid Domicilio domicilio) {
    }

    record Domicilio(@NotBlank(message = "calle obligatoria") String calle) {
    }

    record Filtro(Integer pagina) {
    }
}
