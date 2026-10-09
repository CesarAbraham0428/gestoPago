package com.proyecto.servicios.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.proyecto.servicios.entity.sf.*;
import com.proyecto.servicios.repositorys.sf.*;
import com.proyecto.servicios.service.Impl.ClienteServiceImpl;
import com.proyecto.servicios.service.exception.*;
import jakarta.persistence.EntityManager;
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
            encoder, em, mapper, factory.getValidator());
    }
    @Test void modificaLasCuatroCategoriasYConservaCamposOmitidos() throws Exception {
        prepararLectura(); prepararRespuesta();
        var response = service.actualizarParcial(7, mapper.readTree("""
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
        comprobarRechazoSinCambios(mapper.createObjectNode().put(campo, "cambio"));
    }
    @ParameterizedTest @ValueSource(strings={"{\"primerNombre\":null}", "{\"telefonoMovil\":\"123\"}",
        "{\"domicilio\":{\"codigoPostal\":\"123\"}}", "{\"domicilio\":{\"id\":8}}",
        "{\"sexo\":\"valor-invalido\"}", "{\"fechaNacimiento\":\"2999-01-01\"}",
        "{\"ingresoMensual\":0}"})
    void rechazaDatosInvalidosSinMutarEntidades(String json) throws Exception {
        prepararLectura();
        comprobarRechazoSinCambios(mapper.readTree(json));
    }
    @ParameterizedTest @ValueSource(strings={"[]", "null", "\"texto\""})
    void exigeUnObjetoJsonAntesDeConsultarLaBase(String json) throws Exception {
        JsonNode body = mapper.readTree(json);
        assertThrows(ValidacionException.class, () -> service.actualizarParcial(7, body));
        verifyNoInteractions(clientes, domicilios, cuentas, usuarios, em);
    }
    @Test void nullExplicitoLimpiaUnCampoOpcional() throws Exception {
        prepararLectura(); prepararRespuesta();
        var response = service.actualizarParcial(7, mapper.readTree("{\"segundoNombre\":null}"));
        assertNull(response.segundoNombre());
        assertEquals("Ana", response.primerNombre());
        verify(em).flush();
    }
    @Test void clienteInexistenteNoEscribe() {
        when(clientes.bloquearPorId(7)).thenReturn(Optional.empty());
        assertThrows(ClienteNoEncontradoException.class,
            () -> service.actualizarParcial(7, mapper.createObjectNode().put("empresa", "Nueva")));
        verifyNoInteractions(domicilios, cuentas, usuarios, em);
    }
    @Test void clienteInactivoNoPuedeActualizarse() {
        prepararLectura(); cliente.setActivo(false);
        var exception = assertThrows(NegocioException.class,
            () -> service.actualizarParcial(7, mapper.createObjectNode().put("empresa", "Nueva")));
        assertEquals(HttpStatus.CONFLICT, exception.getStatus());
        assertEquals("Empresa", cliente.getEmpresa());
        verify(em, never()).flush();
    }
    @Test void correoDuplicadoNoMutaElClienteNiElDomicilio() {
        prepararLectura();
        when(clientes.existsByCorreoElectronicoIgnoreCaseAndIdNot("otra@example.com", 7)).thenReturn(true);
        JsonNode cambios = mapper.createObjectNode().put("correoElectronico", "otra@example.com");
        assertThrows(CorreoDuplicadoException.class, () -> service.actualizarParcial(7, cambios));
        assertEquals("ana@example.com", cliente.getCorreoElectronico());
        verify(em, never()).flush();
    }
    private void comprobarRechazoSinCambios(JsonNode cambios) {
        JsonNode clienteAntes = mapper.valueToTree(cliente);
        JsonNode domicilioAntes = mapper.valueToTree(domicilio);
        assertThrows(ValidacionException.class, () -> service.actualizarParcial(7, cambios));
        assertEquals(clienteAntes, mapper.valueToTree(cliente));
        assertEquals(domicilioAntes, mapper.valueToTree(domicilio));
        verify(em, never()).flush();
    }
    private void prepararLectura() {
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
