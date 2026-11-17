package com.proyecto.servicios.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.proyecto.servicios.entity.sf.Cliente;
import com.proyecto.servicios.entity.sf.Cuenta;
import com.proyecto.servicios.entity.sf.Domicilio;
import com.proyecto.servicios.entity.sf.Usuario;
import com.proyecto.servicios.models.request.ClienteConsultaRequest;
import com.proyecto.servicios.repositorys.sf.ClientesRepository;
import com.proyecto.servicios.repositorys.sf.CuentaRepository;
import com.proyecto.servicios.repositorys.sf.DomicilioRepository;
import com.proyecto.servicios.repositorys.sf.UsuarioRepository;
import com.proyecto.servicios.service.Impl.ClienteServiceImpl;
import jakarta.persistence.EntityManager;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ClienteConsultaTest {
    private static final Instant CREACION = Instant.parse("2026-10-01T12:00:00Z");
    private static final Instant ACTUALIZACION = Instant.parse("2026-10-02T12:00:00Z");
    private static ValidatorFactory factory;
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    private final ClientesRepository clientes = mock(ClientesRepository.class);
    private final DomicilioRepository domicilios = mock(DomicilioRepository.class);
    private final CuentaRepository cuentas = mock(CuentaRepository.class);
    private final UsuarioRepository usuarios = mock(UsuarioRepository.class);
    private final AutorizacionService autorizacion = mock(AutorizacionService.class);

    @BeforeAll static void crearValidador() { factory = Validation.buildDefaultValidatorFactory(); }
    @AfterAll static void cerrarValidador() { factory.close(); }

    @Test
    void consultaPaginaClientesCompletosYCargaSusRelacionesEnLote() throws Exception {
        Cliente primero = cliente(7, "ana@example.com");
        Cliente segundo = cliente(8, "luisa@example.com");
        Domicilio domicilioPrimero = domicilio(primero, "Reforma");
        Domicilio domicilioSegundo = domicilio(segundo, "Insurgentes");
        Usuario usuarioPrimero = usuario(primero);
        Usuario usuarioSegundo = usuario(segundo);
        Cuenta cuentaPrimera = cuenta(10, primero, "000000000000000001", "1250.75");
        Cuenta cuentaSegunda = cuenta(11, primero, "000000000000000002", "200.00");
        Cuenta cuentaOtroCliente = cuenta(12, segundo, "000000000000000003", "900.00");
        when(autorizacion.clienteIdParaConsulta(9)).thenReturn(null);
        when(clientes.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(primero, segundo), PageRequest.of(1, 2), 5));
        // El orden de las relaciones no coincide con el de los clientes de la página.
        when(domicilios.findByClienteIdIn(List.of(7, 8))).thenReturn(List.of(domicilioSegundo, domicilioPrimero));
        when(usuarios.findByClienteIdIn(List.of(7, 8))).thenReturn(List.of(usuarioSegundo, usuarioPrimero));
        when(cuentas.findByClienteIdInOrderByIdAsc(List.of(7, 8)))
                .thenReturn(List.of(cuentaPrimera, cuentaSegunda, cuentaOtroCliente));
        ClienteServiceImpl service = new ClienteServiceImpl(clientes, domicilios, cuentas, usuarios,
                mock(PasswordEncoder.class), mock(EntityManager.class), mapper, factory.getValidator(), autorizacion);
        ClienteConsultaRequest filtros = mapper.readValue("{\"pagina\":1,\"tamanio\":2}", ClienteConsultaRequest.class);

        var resultado = service.consultar(filtros, 9);

        assertEquals(1, resultado.pagina());
        assertEquals(2, resultado.tamanoPagina());
        assertEquals(5, resultado.totalElementos());
        assertEquals(3, resultado.totalPaginas());
        var cliente = resultado.datos().get(0);
        assertAll(
                () -> assertEquals(7, cliente.id()),
                () -> assertEquals("Ana", cliente.primerNombre()),
                () -> assertEquals("Maria", cliente.segundoNombre()),
                () -> assertEquals("Perez", cliente.apellidoPaterno()),
                () -> assertEquals("Ruiz", cliente.apellidoMaterno()),
                () -> assertEquals("1990-01-01", cliente.fechaNacimiento().toString()),
                () -> assertEquals("PERA900101MDFRZN01", cliente.curp()),
                () -> assertEquals("PERA900101AB1", cliente.rfc()),
                () -> assertEquals("Femenino", cliente.sexo().name()),
                () -> assertEquals("Mexicana", cliente.nacionalidad()),
                () -> assertEquals("ana@example.com", cliente.correoElectronico()),
                () -> assertEquals("5512345678", cliente.telefonoMovil()),
                () -> assertEquals("5587654321", cliente.telefonoAlternativo()),
                () -> assertEquals("Ingeniera", cliente.ocupacion()),
                () -> assertEquals("Empresa", cliente.empresa()),
                () -> assertEquals(new BigDecimal("12000.50"), cliente.ingresoMensual()),
                () -> assertEquals("Cliente", cliente.rol().name()),
                () -> assertTrue(cliente.activo()),
                () -> assertEquals(CREACION, cliente.fechaCreacion()),
                () -> assertEquals(ACTUALIZACION, cliente.fechaActualizacion()),
                () -> assertEquals("Reforma", cliente.domicilio().calle()),
                () -> assertEquals("06000", cliente.domicilio().codigoPostal()),
                () -> assertEquals(2, cliente.cuentas().size()),
                () -> assertEquals(new BigDecimal("1250.75"), cliente.cuentas().get(0).saldo()),
                () -> assertEquals(CREACION, cliente.cuentas().get(0).fechaCreacion()),
                () -> assertEquals(ACTUALIZACION, cliente.cuentas().get(0).fechaActualizacion()),
                () -> assertEquals("ana@example.com", cliente.usuario().correo()),
                () -> assertEquals(CREACION, cliente.usuario().fechaCreacion()),
                () -> assertEquals(ACTUALIZACION, cliente.usuario().fechaActualizacion()));
        var otro = resultado.datos().get(1);
        assertEquals(8, otro.id());
        assertEquals("Insurgentes", otro.domicilio().calle());
        assertEquals("luisa@example.com", otro.usuario().correo());
        assertEquals(List.of("000000000000000003"), otro.cuentas().stream().map(c -> c.numeroCuenta()).toList());
        var json = mapper.valueToTree(cliente);
        assertFalse(json.get("usuario").has("passwordHash"));
        assertFalse(json.get("usuario").has("password"));
        verify(clientes).findAll(any(Specification.class), argThat((Pageable p) ->
                p.getPageNumber() == 1 && p.getPageSize() == 2 && p.getSort().getOrderFor("id") != null));
        verify(domicilios).findByClienteIdIn(List.of(7, 8));
        verify(usuarios).findByClienteIdIn(List.of(7, 8));
        verify(cuentas).findByClienteIdInOrderByIdAsc(List.of(7, 8));
        verifyNoMoreInteractions(domicilios, usuarios, cuentas);
    }

    private Cliente cliente(int id, String correo) throws Exception {
        Cliente cliente = mapper.readValue("""
                {"primerNombre":"Ana","segundoNombre":"Maria","apellidoPaterno":"Perez",
                 "apellidoMaterno":"Ruiz","fechaNacimiento":"1990-01-01","curp":"PERA900101MDFRZN01",
                 "rfc":"PERA900101AB1","sexo":"Femenino","nacionalidad":"Mexicana","estadoCivil":"Soltero",
                 "telefonoMovil":"5512345678","telefonoAlternativo":"5587654321","ocupacion":"Ingeniera",
                 "empresa":"Empresa","ingresoMensual":12000.50}
                """, Cliente.class);
        cliente.setId(id);
        cliente.setCorreoElectronico(correo);
        fechas(cliente);
        return cliente;
    }

    private Domicilio domicilio(Cliente cliente, String calle) throws Exception {
        Domicilio domicilio = mapper.readValue("""
                {"id":20,"numeroExterior":"10","colonia":"Centro","municipio":"Cuauhtemoc",
                 "estado":"CDMX","codigoPostal":"06000","pais":"Mexico"}
                """, Domicilio.class);
        domicilio.setCalle(calle);
        domicilio.setCliente(cliente);
        return domicilio;
    }

    private Usuario usuario(Cliente cliente) {
        Usuario usuario = new Usuario();
        usuario.setId(cliente.getId() + 20);
        usuario.setCliente(cliente);
        usuario.setCorreo(cliente.getCorreoElectronico());
        usuario.setPasswordHash("credencial-que-no-debe-exponerse");
        fechas(usuario);
        return usuario;
    }

    private Cuenta cuenta(int id, Cliente cliente, String numero, String saldo) {
        Cuenta cuenta = new Cuenta();
        cuenta.setId(id);
        cuenta.setCliente(cliente);
        cuenta.setNumeroCuenta(numero);
        cuenta.setSaldo(new BigDecimal(saldo));
        fechas(cuenta);
        return cuenta;
    }

    private void fechas(Object entidad) {
        ReflectionTestUtils.setField(entidad, "fechaCreacion", CREACION);
        ReflectionTestUtils.setField(entidad, "fechaActualizacion", ACTUALIZACION);
    }
}
