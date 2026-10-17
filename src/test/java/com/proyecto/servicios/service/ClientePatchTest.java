package com.proyecto.servicios.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.proyecto.servicios.entity.sf.*;
import com.proyecto.servicios.models.request.ClienteActualizacionPorIdentidadRequest;
import com.proyecto.servicios.models.request.ClienteIdentificacionRequest;
import com.proyecto.servicios.models.response.ClienteResponse;
import com.proyecto.servicios.repositorys.sf.*;
import com.proyecto.servicios.service.Impl.ClienteServiceImpl;
import com.proyecto.servicios.service.exception.*;
import jakarta.persistence.EntityManager;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class ClientePatchTest {
    private static ValidatorFactory factory;
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    @Mock ClientesRepository clientes;
    @Mock DomicilioRepository domicilios;
    @Mock CuentaRepository cuentas;
    @Mock UsuarioRepository usuarios;
    @Mock EntityManager em;
    @Mock PasswordEncoder encoder;
    @Mock AutorizacionService autorizacion;
    private ClienteServiceImpl service;
    private Cliente cliente;
    private Domicilio domicilio;

    @BeforeAll static void crearValidador() { factory = Validation.buildDefaultValidatorFactory(); }
    @AfterAll static void cerrarValidador() { factory.close(); }
    @BeforeEach void preparar() throws Exception {
        cliente = mapper.readValue("""
            {"id":7,"primerNombre":"Ana","segundoNombre":"Maria","apellidoPaterno":"Perez",
             "apellidoMaterno":"Ruiz","fechaNacimiento":"1990-01-01","curp":"PERA900101MDFRZN01",
             "rfc":"PERA900101AB1","sexo":"Femenino","nacionalidad":"Mexicana","estadoCivil":"Soltero",
             "correoElectronico":"ana@example.com","telefonoMovil":"5512345678","ocupacion":"Ingeniera",
             "empresa":"Empresa","ingresoMensual":12000}
            """, Cliente.class);
        domicilio = mapper.readValue("""
            {"id":8,"calle":"Reforma","numeroExterior":"10","colonia":"Centro","municipio":"Cuauhtemoc",
             "estado":"CDMX","codigoPostal":"06000","pais":"Mexico"}
            """, Domicilio.class);
        domicilio.setCliente(cliente);
        service = new ClienteServiceImpl(clientes, domicilios, cuentas, usuarios,
            encoder, em, mapper, factory.getValidator(), autorizacion);
    }
    @Test void modificaLasCuatroCategoriasYConservaCamposOmitidos() throws Exception {
        prepararLectura(); prepararRespuesta();
        var response = actualizar(mapper.readTree("""
            {"primerNombre":"Luisa","telefonoMovil":"5598765432",
             "domicilio":{"calle":"Insurgentes"},"empresa":"Nueva empresa","ingresoMensual":15000}
            """));
        assertAll(
            () -> assertEquals("Luisa", response.primerNombre()),
            () -> assertEquals("5598765432", response.telefonoMovil()),
            () -> assertEquals("Insurgentes", response.domicilio().calle()),
            () -> assertEquals("Nueva empresa", response.empresa()),
            () -> assertEquals(0, new BigDecimal("15000").compareTo(response.ingresoMensual())),
            () -> assertEquals("Perez", response.apellidoPaterno()),
            () -> assertEquals("06000", response.domicilio().codigoPostal()),
            () -> assertEquals("PERA900101MDFRZN01", response.curp()),
            () -> assertEquals("PERA900101AB1", response.rfc()),
            () -> assertEquals("000000000000000001", response.cuentas().get(0).numeroCuenta()));
        verify(em).flush();
    }
    @ParameterizedTest @ValueSource(strings={"curp", "rfc", "numeroCuenta", "cuentas", "activo", "rol", "password"})
    void rechazaCamposProtegidosSinMutarEntidades(String campo) {
        prepararLectura();
        comprobarRechazoSinCambios(mapper.createObjectNode().put(campo, "cambio"), ValidacionException.class);
    }
    @ParameterizedTest @ValueSource(strings={"{\"primerNombre\":null}", "{\"telefonoMovil\":\"123\"}",
        "{\"domicilio\":{\"codigoPostal\":\"123\"}}", "{\"fechaNacimiento\":\"2999-01-01\"}",
        "{\"ingresoMensual\":0}"})
    void rechazaDatosInvalidosSinMutarEntidades(String json) throws Exception {
        prepararLectura();
        comprobarRechazoSinCambios(mapper.readTree(json), ConstraintViolationException.class);
    }
    @ParameterizedTest @ValueSource(strings={"{\"domicilio\":{\"id\":8}}", "{\"sexo\":\"valor-invalido\"}"})
    void rechazaFormatosOCamposAnidadosNoPermitidosSinMutarEntidades(String json) throws Exception {
        prepararLectura();
        comprobarRechazoSinCambios(mapper.readTree(json), ValidacionException.class);
    }
    @ParameterizedTest @ValueSource(strings={"[]", "null", "\"texto\"", "{}"})
    void exigeUnObjetoJsonAntesDeConsultarLaBase(String json) throws Exception {
        JsonNode body = mapper.readTree(json);
        assertThrows(ConstraintViolationException.class, () -> actualizar(body));
        verifyNoInteractions(clientes, domicilios, cuentas, usuarios, em, autorizacion);
    }
    @Test void identificacionAusenteSeRechazaAntesDeConsultarLaBase() {
        var request = new ClienteActualizacionPorIdentidadRequest(
            new ClienteIdentificacionRequest(null, null), mapper.createObjectNode().put("empresa", "Nueva"));
        assertThrows(ConstraintViolationException.class, () -> service.actualizarParcial(request, 9));
        verifyNoInteractions(clientes, domicilios, cuentas, usuarios, em, autorizacion);
    }
    @Test void nullExplicitoLimpiaUnCampoOpcional() throws Exception {
        prepararLectura(); prepararRespuesta();
        var response = actualizar(mapper.readTree("{\"segundoNombre\":null}"));
        assertNull(response.segundoNombre());
        assertEquals("Ana", response.primerNombre());
        verify(em).flush();
    }
    @Test void conservaCambiosConcurrentesEnCamposOmitidosDelPatch() throws Exception {
        prepararLectura(); prepararRespuesta();
        doAnswer(invocacion -> {
            cliente.setEmpresa("Empresa actualizada por otra transacción");
            cliente.setTelefonoMovil("5587654321");
            return null;
        }).when(em).refresh(cliente);

        var response = actualizar(mapper.readTree("{\"primerNombre\":\"Luisa\"}"));

        assertAll(
            () -> assertEquals("Luisa", response.primerNombre()),
            () -> assertEquals("Empresa actualizada por otra transacción", response.empresa()),
            () -> assertEquals("5587654321", response.telefonoMovil()),
            () -> assertEquals("Perez", response.apellidoPaterno())
        );
        verify(em).flush();
    }
    @Test void clienteInexistenteNoEscribe() {
        when(clientes.findByRfcIgnoreCase("PERA900101AB1")).thenReturn(Optional.empty());
        assertThrows(ClienteNoEncontradoException.class,
            () -> actualizar(mapper.createObjectNode().put("empresa", "Nueva")));
        verify(clientes, never()).bloquearPorId(anyInt());
        verifyNoInteractions(domicilios, cuentas, usuarios, em, autorizacion);
    }
    @Test void clienteInactivoNoPuedeActualizarse() {
        prepararLectura(); cliente.setActivo(false);
        var exception = assertThrows(NegocioException.class,
            () -> actualizar(mapper.createObjectNode().put("empresa", "Nueva")));
        assertEquals(HttpStatus.CONFLICT, exception.getStatus());
        assertEquals("Empresa", cliente.getEmpresa());
        verify(em, never()).flush();
    }
    @Test void correoDuplicadoNoMutaElClienteNiElDomicilio() {
        prepararLectura();
        when(clientes.existsByCorreoElectronicoIgnoreCaseAndIdNot("otra@example.com", 7)).thenReturn(true);
        JsonNode cambios = mapper.createObjectNode().put("correoElectronico", "otra@example.com");
        assertThrows(CorreoDuplicadoException.class, () -> actualizar(cambios));
        assertEquals("ana@example.com", cliente.getCorreoElectronico());
        verify(em, never()).flush();
    }
    @Test void clienteAjenoSeRechazaAntesDeBloquearOCargarSusRelaciones() {
        when(clientes.findByRfcIgnoreCase("PERA900101AB1")).thenReturn(Optional.of(cliente));
        doThrow(new NegocioException(HttpStatus.FORBIDDEN, "Acceso denegado"))
            .when(autorizacion).autorizarCliente(7, 9);
        var exception = assertThrows(NegocioException.class,
            () -> actualizar(mapper.createObjectNode().put("empresa", "No autorizado")));
        assertEquals(HttpStatus.FORBIDDEN, exception.getStatus());
        assertEquals("Empresa", cliente.getEmpresa());
        verify(clientes, never()).bloquearPorId(anyInt());
        verifyNoInteractions(domicilios, cuentas, usuarios, em);
    }
    @Test void rfcYCurpDebenIdentificarAlMismoClienteAntesDeEscribir() {
        when(clientes.findByRfcIgnoreCaseAndCurpIgnoreCase("PERA900101AB1", "PERA900101MDFRZN02"))
            .thenReturn(Optional.empty());
        var request = new ClienteActualizacionPorIdentidadRequest(
            new ClienteIdentificacionRequest("PERA900101AB1", "PERA900101MDFRZN02"),
            mapper.createObjectNode().put("empresa", "No guardar"));
        assertThrows(ClienteNoEncontradoException.class, () -> service.actualizarParcial(request, 9));
        verify(clientes, never()).findByRfcIgnoreCase(anyString());
        verify(clientes, never()).findByCurpIgnoreCase(anyString());
        verify(clientes, never()).bloquearPorId(anyInt());
        verifyNoInteractions(domicilios, cuentas, usuarios, em, autorizacion);
        assertEquals("Empresa", cliente.getEmpresa());
    }
    private ClienteResponse actualizar(JsonNode cambios) {
        var request = new ClienteActualizacionPorIdentidadRequest(
            new ClienteIdentificacionRequest("PERA900101AB1", null), cambios);
        return service.actualizarParcial(request, 9);
    }
    private void comprobarRechazoSinCambios(JsonNode cambios, Class<? extends RuntimeException> tipo) {
        JsonNode clienteAntes = mapper.valueToTree(cliente);
        JsonNode domicilioAntes = mapper.valueToTree(domicilio);
        assertThrows(tipo, () -> actualizar(cambios));
        assertEquals(clienteAntes, mapper.valueToTree(cliente));
        assertEquals(domicilioAntes, mapper.valueToTree(domicilio));
        verify(em, never()).flush();
    }

    @Test void patchConservaTodosLosErroresDeBeanValidationSinModificarEntidades() throws Exception {
        prepararLectura();
        var exception = assertThrows(ConstraintViolationException.class, () -> actualizar(mapper.readTree("""
            {"telefonoMovil":"123","domicilio":{"codigoPostal":"123"}}
            """)));
        var campos = exception.getConstraintViolations().stream()
                .map(error -> error.getPropertyPath().toString()).toList();
        assertTrue(campos.containsAll(List.of("telefonoMovil", "domicilio.codigoPostal")));
        assertEquals("5512345678", cliente.getTelefonoMovil());
        assertEquals("06000", domicilio.getCodigoPostal());
        verify(em, never()).flush();
    }
    private void prepararLectura() {
        when(clientes.findByRfcIgnoreCase("PERA900101AB1")).thenReturn(Optional.of(cliente));
        when(clientes.bloquearPorId(7)).thenReturn(Optional.of(cliente));
        when(domicilios.findByClienteId(7)).thenReturn(Optional.of(domicilio));
    }
    private void prepararRespuesta() {
        when(clientes.findById(7)).thenReturn(Optional.of(cliente));
        Usuario usuario = new Usuario(); usuario.setId(9); usuario.setCliente(cliente);
        usuario.setCorreo("ana@example.com");
        when(usuarios.findByClienteId(7)).thenReturn(Optional.of(usuario));
        Cuenta cuenta = new Cuenta(); cuenta.setId(10); cuenta.setCliente(cliente);
        cuenta.setNumeroCuenta("000000000000000001");
        when(cuentas.findByClienteIdOrderByIdAsc(7)).thenReturn(List.of(cuenta));
    }
}
