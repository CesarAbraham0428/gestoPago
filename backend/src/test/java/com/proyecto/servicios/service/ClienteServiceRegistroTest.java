package com.proyecto.servicios.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.proyecto.servicios.entity.sf.Cliente;
import com.proyecto.servicios.entity.sf.Cuenta;
import com.proyecto.servicios.entity.sf.Domicilio;
import com.proyecto.servicios.entity.sf.EstadoCivil;
import com.proyecto.servicios.entity.sf.Sexo;
import com.proyecto.servicios.entity.sf.Usuario;
import com.proyecto.servicios.models.request.ClienteActualizacionPorIdentidadRequest;
import com.proyecto.servicios.models.request.ClienteConsultaRequest;
import com.proyecto.servicios.models.request.ClienteIdentificacionRequest;
import com.proyecto.servicios.models.request.ClienteDisponibilidadRequest;
import com.proyecto.servicios.models.request.ClienteRequest;
import com.proyecto.servicios.models.request.DomicilioRequest;
import com.proyecto.servicios.models.response.ClienteResponse;
import com.proyecto.servicios.repositorys.sf.ClientesRepository;
import com.proyecto.servicios.repositorys.sf.CuentaRepository;
import com.proyecto.servicios.repositorys.sf.DomicilioRepository;
import com.proyecto.servicios.repositorys.sf.UsuarioRepository;
import com.proyecto.servicios.service.Impl.ClienteServiceImpl;
import com.proyecto.servicios.service.exception.ClienteNoEncontradoException;
import com.proyecto.servicios.service.exception.CorreoDuplicadoException;
import com.proyecto.servicios.service.exception.DatosDuplicadosException;
import com.proyecto.servicios.service.exception.NegocioException;
import com.proyecto.servicios.service.exception.UsuarioNoEncontradoException;
import com.proyecto.servicios.service.exception.ValidacionException;
import jakarta.persistence.EntityManager;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;

/** Registro, baja y rutas de error de ClienteServiceImpl (las consultas y el PATCH feliz tienen sus clases). */
class ClienteServiceRegistroTest {

    private static final String PASSWORD = "Segura#2026";
    private static ValidatorFactory factory;

    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    private final ClientesRepository clientes = mock(ClientesRepository.class);
    private final DomicilioRepository domicilios = mock(DomicilioRepository.class);
    private final CuentaRepository cuentas = mock(CuentaRepository.class);
    private final UsuarioRepository usuarios = mock(UsuarioRepository.class);
    private final PasswordEncoder encoder = mock(PasswordEncoder.class);
    private final EntityManager em = mock(EntityManager.class);
    private final AutorizacionService autorizacion = mock(AutorizacionService.class);
    private ClienteServiceImpl service;

    @BeforeAll
    static void crearValidador() {
        factory = Validation.buildDefaultValidatorFactory();
    }

    @AfterAll
    static void cerrarValidador() {
        factory.close();
    }

    private ClienteServiceImpl servicio() {
        if (service == null) {
            service = new ClienteServiceImpl(clientes, domicilios, cuentas, usuarios, encoder, em, mapper,
                    factory.getValidator(), autorizacion);
        }
        return service;
    }

    private static DomicilioRequest domicilioRequest() {
        return new DomicilioRequest(" Reforma ", " 10 ", " 2B ", " Centro ", " Cuauhtémoc ", " CDMX ", "06000",
                " México ");
    }

    private static ClienteRequest solicitud(LocalDate nacimiento, String password) {
        return new ClienteRequest(" Ana ", " María ", " Pérez ", " Ruiz ", nacimiento, " pera900101mdfrzn01 ",
                " pera900101ab1 ", Sexo.Femenino, " Mexicana ", EstadoCivil.Soltero, " Ana@Example.COM ",
                "5512345678", "5587654321", " Ingeniera ", " Empresa ", new BigDecimal("12000.50"),
                domicilioRequest(), password);
    }

    @Test
    void registrarGuardaClienteDomicilioCuentaYUsuarioNormalizados() {
        when(cuentas.siguienteNumero()).thenReturn("000000000000000001");
        when(encoder.encode(PASSWORD)).thenReturn("hash");

        ClienteResponse respuesta = servicio().registrar(solicitud(LocalDate.of(1990, 1, 1), PASSWORD));

        ArgumentCaptor<Cliente> cliente = ArgumentCaptor.forClass(Cliente.class);
        verify(clientes).saveAndFlush(cliente.capture());
        assertEquals("Ana", cliente.getValue().getPrimerNombre());
        assertEquals("María", cliente.getValue().getSegundoNombre());
        assertEquals("PERA900101MDFRZN01", cliente.getValue().getCurp());
        assertEquals("PERA900101AB1", cliente.getValue().getRfc());
        assertEquals("ana@example.com", cliente.getValue().getCorreoElectronico());
        ArgumentCaptor<Domicilio> domicilio = ArgumentCaptor.forClass(Domicilio.class);
        verify(domicilios).save(domicilio.capture());
        assertEquals("Reforma", domicilio.getValue().getCalle());
        assertEquals("2B", domicilio.getValue().getNumeroInterior());
        ArgumentCaptor<Cuenta> cuenta = ArgumentCaptor.forClass(Cuenta.class);
        verify(cuentas).save(cuenta.capture());
        assertEquals("000000000000000001", cuenta.getValue().getNumeroCuenta());
        assertEquals(0, BigDecimal.ZERO.compareTo(cuenta.getValue().getSaldo()));
        ArgumentCaptor<Usuario> usuario = ArgumentCaptor.forClass(Usuario.class);
        verify(usuarios).save(usuario.capture());
        assertEquals("hash", usuario.getValue().getPasswordHash());
        assertEquals("ana@example.com", usuario.getValue().getCorreo());
        verify(em).flush();
        assertEquals("ana@example.com", respuesta.correoElectronico());
        assertEquals(1, respuesta.cuentas().size());
    }

    @Test
    void registrarSinOcupacionNiDomicilioSoloCreaClienteCuentaYUsuario() {
        when(cuentas.siguienteNumero()).thenReturn("000000000000000001");
        when(encoder.encode(PASSWORD)).thenReturn("hash");
        ClienteRequest base = solicitud(LocalDate.of(1990, 1, 1), PASSWORD);
        ClienteRequest sinOpcionales = new ClienteRequest(base.primerNombre(), base.segundoNombre(),
                base.apellidoPaterno(), base.apellidoMaterno(), base.fechaNacimiento(), base.curp(), base.rfc(),
                base.sexo(), base.nacionalidad(), base.estadoCivil(), base.correoElectronico(),
                base.telefonoMovil(), base.telefonoAlternativo(), null, null, null, null, PASSWORD);

        ClienteResponse respuesta = servicio().registrar(sinOpcionales);

        ArgumentCaptor<Cliente> cliente = ArgumentCaptor.forClass(Cliente.class);
        verify(clientes).saveAndFlush(cliente.capture());
        assertNull(cliente.getValue().getOcupacion());
        assertNull(cliente.getValue().getEmpresa());
        assertNull(cliente.getValue().getIngresoMensual());
        verify(domicilios, never()).save(any());
        verify(cuentas).save(any(Cuenta.class));
        verify(usuarios).save(any(Usuario.class));
        assertNull(respuesta.domicilio());
        assertNull(respuesta.ocupacion());
    }

    @Test
    void registrarRechazaMenoresDeEdadYContrasenasDebiles() {
        LocalDate menor = LocalDate.now().minusYears(10);

        assertThrows(ValidacionException.class, () -> servicio().registrar(solicitud(menor, PASSWORD)));
        assertThrows(ValidacionException.class,
                () -> servicio().registrar(solicitud(LocalDate.of(1990, 1, 1), "123")));
        assertThrows(ValidacionException.class, () -> servicio().registrar(solicitud(null, PASSWORD)));
        verify(clientes, never()).saveAndFlush(any());
    }

    @Test
    void registrarNombraCadaDatoDuplicado() {
        ClienteRequest request = solicitud(LocalDate.of(1990, 1, 1), PASSWORD);

        when(clientes.existsByCurp("PERA900101MDFRZN01")).thenReturn(true);
        assertEquals("El CURP ya está registrado", mensajeDeRegistro(request));

        when(clientes.existsByRfc("PERA900101AB1")).thenReturn(true);
        assertEquals("Ya están registrados: CURP y RFC", mensajeDeRegistro(request));

        when(clientes.existsByCurp(anyString())).thenReturn(false);
        when(clientes.existsByRfc(anyString())).thenReturn(false);
        when(clientes.existsByTelefonoMovil(request.telefonoMovil())).thenReturn(true);
        when(clientes.existsByCorreoElectronicoIgnoreCase("ana@example.com")).thenReturn(true);
        assertEquals("Ya están registrados: teléfono móvil y correo electrónico", mensajeDeRegistro(request));
        verify(clientes, never()).saveAndFlush(any());
    }

    @Test
    void datosDuplicadosSoloConsultaLosCamposEnviados() {
        when(clientes.existsByRfc("PERA900101AB1")).thenReturn(true);

        assertEquals(List.of(DatosDuplicadosException.RFC), servicio().datosDuplicados(
                new ClienteDisponibilidadRequest(null, "pera900101ab1", null, null)));
        assertEquals(List.of(), servicio().datosDuplicados(new ClienteDisponibilidadRequest(null, null, null, null)));
        verify(clientes, never()).existsByCurp(anyString());
    }

    private String mensajeDeRegistro(ClienteRequest request) {
        return assertThrows(DatosDuplicadosException.class, () -> servicio().registrar(request)).getMessage();
    }

    @Test
    void consultarConUnaPaginaVaciaNoCargaRelaciones() {
        when(autorizacion.clienteIdParaConsulta(9)).thenReturn(null);
        when(clientes.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        var respuesta = servicio().consultar(new ClienteConsultaRequest(), 9);

        assertEquals(0, respuesta.totalElementos());
        verify(domicilios, never()).findByClienteIdIn(any());
    }

    @Test
    void consultarFallaSiAUnClienteDeLaPaginaLeFaltaDomicilioOUsuario() {
        Cliente cliente = new Cliente();
        cliente.setId(7);
        Domicilio domicilio = new Domicilio();
        domicilio.setCliente(cliente);
        when(clientes.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(cliente), PageRequest.of(0, 20), 1));
        when(domicilios.findByClienteIdIn(List.of(7))).thenReturn(List.of());
        when(usuarios.findByClienteIdIn(List.of(7))).thenReturn(List.of());
        when(cuentas.findByClienteIdInOrderByIdAsc(List.of(7))).thenReturn(List.of());

        NegocioException sinDomicilio = assertThrows(NegocioException.class,
                () -> servicio().consultar(new ClienteConsultaRequest(), 9));
        assertEquals(HttpStatus.NOT_FOUND, sinDomicilio.getStatus());

        when(domicilios.findByClienteIdIn(List.of(7))).thenReturn(List.of(domicilio));
        assertThrows(UsuarioNoEncontradoException.class,
                () -> servicio().consultar(new ClienteConsultaRequest(), 9));
    }

    @Test
    void consultarExigeUnaSolicitudValida() {
        assertThrows(ValidacionException.class, () -> servicio().consultar(null, 9));
        ClienteConsultaRequest invalida = new ClienteConsultaRequest();
        invalida.setPagina(-1);
        assertThrows(jakarta.validation.ConstraintViolationException.class, () -> servicio().consultar(invalida, 9));
    }

    @Test
    void desactivarBloqueaAlClienteLoDaDeBajaYLimpiaElContexto() {
        Cliente cliente = new Cliente();
        cliente.setId(7);
        when(clientes.findByRfcIgnoreCase("PERA900101AB1")).thenReturn(Optional.of(cliente));
        when(clientes.bloquearPorId(7)).thenReturn(Optional.of(cliente));

        servicio().desactivar(new ClienteIdentificacionRequest("pera900101ab1", null), 9);

        assertFalse(cliente.isActivo());
        verify(autorizacion).autorizarCliente(7, 9);
        verify(em).refresh(cliente);
        verify(em).flush();
        verify(em).clear();
    }

    @Test
    void laIdentificacionPuedeSerPorCurpOPorAmbos() {
        Cliente cliente = new Cliente();
        cliente.setId(7);
        when(clientes.findByCurpIgnoreCase("PERA900101MDFRZN01")).thenReturn(Optional.of(cliente));
        when(clientes.findByRfcIgnoreCaseAndCurpIgnoreCase("PERA900101AB1", "PERA900101MDFRZN01"))
                .thenReturn(Optional.empty());
        when(clientes.bloquearPorId(7)).thenReturn(Optional.of(cliente));

        servicio().desactivar(new ClienteIdentificacionRequest(null, "PERA900101MDFRZN01"), 9);
        assertFalse(cliente.isActivo());

        assertThrows(ClienteNoEncontradoException.class, () -> servicio().desactivar(
                new ClienteIdentificacionRequest("PERA900101AB1", "PERA900101MDFRZN01"), 9));
        verify(clientes).findByRfcIgnoreCaseAndCurpIgnoreCase("PERA900101AB1", "PERA900101MDFRZN01");
    }

    @Test
    void desactivarFallaSiElClienteDesapareceAntesDeBloquearlo() {
        Cliente cliente = new Cliente();
        cliente.setId(7);
        when(clientes.findByRfcIgnoreCase("PERA900101AB1")).thenReturn(Optional.of(cliente));
        when(clientes.bloquearPorId(7)).thenReturn(Optional.empty());

        assertThrows(ClienteNoEncontradoException.class,
                () -> servicio().desactivar(new ClienteIdentificacionRequest("PERA900101AB1", null), 9));
        assertThrows(ValidacionException.class, () -> servicio().desactivar(null, 9));
    }

    // ---------- actualización parcial: rutas de error ----------

    private Cliente clienteActivo(boolean activo) {
        Cliente cliente = new Cliente();
        cliente.setId(7);
        cliente.setPrimerNombre("Ana");
        cliente.setApellidoPaterno("Perez");
        cliente.setApellidoMaterno("Ruiz");
        cliente.setFechaNacimiento(LocalDate.of(1990, 1, 1));
        cliente.setSexo(Sexo.Femenino);
        cliente.setNacionalidad("Mexicana");
        cliente.setEstadoCivil(EstadoCivil.Soltero);
        cliente.setCorreoElectronico("ana@example.com");
        cliente.setTelefonoMovil("5512345678");
        cliente.setOcupacion("Ingeniera");
        cliente.setEmpresa("Empresa");
        cliente.setIngresoMensual(new BigDecimal("12000"));
        cliente.setActivo(activo);
        return cliente;
    }

    private Domicilio domicilio(Cliente cliente) {
        Domicilio domicilio = new Domicilio();
        domicilio.setCliente(cliente);
        domicilio.setCalle("Reforma");
        domicilio.setNumeroExterior("10");
        domicilio.setColonia("Centro");
        domicilio.setMunicipio("Cuauhtemoc");
        domicilio.setEstado("CDMX");
        domicilio.setCodigoPostal("06000");
        domicilio.setPais("Mexico");
        return domicilio;
    }

    private ClienteActualizacionPorIdentidadRequest parche(String json) throws Exception {
        return new ClienteActualizacionPorIdentidadRequest(new ClienteIdentificacionRequest("PERA900101AB1", null),
                mapper.readTree(json));
    }

    @Test
    void elParcheDeUnClienteInactivoSeRechazaConConflicto() throws Exception {
        Cliente cliente = clienteActivo(false);
        when(clientes.findByRfcIgnoreCase("PERA900101AB1")).thenReturn(Optional.of(cliente));
        when(clientes.bloquearPorId(7)).thenReturn(Optional.of(cliente));
        when(domicilios.findByClienteId(7)).thenReturn(Optional.of(domicilio(cliente)));

        NegocioException error = assertThrows(NegocioException.class,
                () -> servicio().actualizarParcial(parche("{\"empresa\":\"Nueva\"}"), 9));

        assertEquals(HttpStatus.CONFLICT, error.getStatus());
    }

    @Test
    void elParcheRechazaUnTelefonoYaUsadoPorOtroCliente() throws Exception {
        Cliente cliente = clienteActivo(true);
        when(clientes.findByRfcIgnoreCase("PERA900101AB1")).thenReturn(Optional.of(cliente));
        when(clientes.bloquearPorId(7)).thenReturn(Optional.of(cliente));
        when(domicilios.findByClienteId(7)).thenReturn(Optional.of(domicilio(cliente)));
        when(clientes.existsByTelefonoMovilAndIdNot("5500000000", 7)).thenReturn(true);

        assertEquals("El teléfono móvil ya está registrado", assertThrows(DatosDuplicadosException.class,
                () -> servicio().actualizarParcial(parche("{\"telefonoMovil\":\"5500000000\"}"), 9)).getMessage());
    }

    @Test
    void elParcheRechazaUnCorreoYaUsadoPorOtroCliente() throws Exception {
        Cliente cliente = clienteActivo(true);
        when(clientes.findByRfcIgnoreCase("PERA900101AB1")).thenReturn(Optional.of(cliente));
        when(clientes.bloquearPorId(7)).thenReturn(Optional.of(cliente));
        when(domicilios.findByClienteId(7)).thenReturn(Optional.of(domicilio(cliente)));
        when(clientes.existsByCorreoElectronicoIgnoreCaseAndIdNot("otro@example.com", 7)).thenReturn(true);

        assertThrows(CorreoDuplicadoException.class,
                () -> servicio().actualizarParcial(parche("{\"correoElectronico\":\"Otro@Example.com\"}"), 9));
    }

    @Test
    void elParcheFallaSiElClienteNoTieneDomicilio() throws Exception {
        Cliente cliente = clienteActivo(true);
        when(clientes.findByRfcIgnoreCase("PERA900101AB1")).thenReturn(Optional.of(cliente));
        when(clientes.bloquearPorId(7)).thenReturn(Optional.of(cliente));
        when(domicilios.findByClienteId(7)).thenReturn(Optional.empty());

        NegocioException error = assertThrows(NegocioException.class,
                () -> servicio().actualizarParcial(parche("{\"empresa\":\"Nueva\"}"), 9));

        assertEquals(HttpStatus.NOT_FOUND, error.getStatus());
    }

    @Test
    void elParcheValidaQueLaSolicitudNoSeaNula() {
        assertThrows(ValidacionException.class, () -> servicio().actualizarParcial(null, 9));
        assertTrue(true);
    }

    @Test
    void laIdentificacionPorRfcYCurpEncuentraAlClienteQueCoincideConAmbos() {
        Cliente cliente = new Cliente();
        cliente.setId(7);
        when(clientes.findByRfcIgnoreCaseAndCurpIgnoreCase("PERA900101AB1", "PERA900101MDFRZN01"))
                .thenReturn(Optional.of(cliente));
        when(clientes.bloquearPorId(7)).thenReturn(Optional.of(cliente));

        servicio().desactivar(new ClienteIdentificacionRequest("pera900101ab1", "pera900101mdfrzn01"), 9);

        assertFalse(cliente.isActivo());
    }
}
