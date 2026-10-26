package com.proyecto.servicios.service;

import com.proyecto.servicios.entity.sf.Cliente;
import com.proyecto.servicios.entity.sf.Cuenta;
import com.proyecto.servicios.entity.sf.Rol;
import com.proyecto.servicios.entity.sf.Usuario;
import com.proyecto.servicios.models.request.CuentaConsultaRequest;
import com.proyecto.servicios.models.response.CuentaResponse;
import com.proyecto.servicios.repositorys.sf.ClientesRepository;
import com.proyecto.servicios.repositorys.sf.CuentaRepository;
import com.proyecto.servicios.repositorys.sf.UsuarioRepository;
import com.proyecto.servicios.service.exception.CuentaNoEncontradaException;
import com.proyecto.servicios.service.exception.NegocioException;
import com.proyecto.servicios.service.exception.ValidacionException;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CuentaServiceTest {

    private static final String NUMERO = "000000000000000001";
    private final CuentaRepository cuentas = mock(CuentaRepository.class);
    private final ClientesRepository clientes = mock(ClientesRepository.class);
    private final UsuarioRepository usuarios = mock(UsuarioRepository.class);
    private final EntityManager em = mock(EntityManager.class);
    private final CuentaService service = new CuentaService(cuentas, clientes, em,
            new AutorizacionService(usuarios), new BCryptPasswordEncoder(4));
    private Usuario usuario;

    @BeforeEach
    void prepararUsuario() {
        Cliente cliente = new Cliente();
        cliente.setId(3);
        usuario = new Usuario();
        usuario.setId(7);
        usuario.setCliente(cliente);
        when(usuarios.findById(7)).thenReturn(Optional.of(usuario));
    }

    @Test
    void paginaIncluyePropietarioEnConsultaAntesDePaginar() {
        var cuenta = new CuentaResponse(4, 3, NUMERO, new BigDecimal("1250.75"), true,
                Instant.parse("2026-10-01T12:00:00Z"), Instant.parse("2026-10-02T12:00:00Z"));
        when(cuentas.consultar(isNull(), isNull(), isNull(), eq(3), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(cuenta), PageRequest.of(2, 10), 21));

        var resultado = service.consultar(new CuentaConsultaRequest(null, null, null, 2, 10), 7);

        assertEquals(21, resultado.totalElementos());
        assertEquals(2, resultado.pagina());
        assertEquals(List.of(cuenta), resultado.datos());
        assertEquals(new BigDecimal("1250.75"), resultado.datos().get(0).saldo());
        assertEquals(cuenta.fechaCreacion(), resultado.datos().get(0).fechaCreacion());
        assertEquals(cuenta.fechaActualizacion(), resultado.datos().get(0).fechaActualizacion());
        verify(cuentas).consultar(isNull(), isNull(), isNull(), eq(3),
                argThat(p -> p.getPageNumber() == 2 && p.getPageSize() == 10));
    }

    @Test
    void administradorPuedeConsultarCuentasDeOtrosClientes() {
        usuario.getCliente().setRol(Rol.Administrador);
        CuentaResponse cuenta = new CuentaResponse(4, 9, NUMERO, BigDecimal.TEN, true, null, null);
        when(cuentas.consultar(isNull(), isNull(), isNull(), isNull(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(cuenta)));

        var resultado = service.consultar(new CuentaConsultaRequest(null, null, null, 0, 20), 7);

        assertEquals(9, resultado.datos().get(0).clienteId());
        assertEquals(BigDecimal.TEN, resultado.datos().get(0).saldo());
        verify(cuentas).consultar(isNull(), isNull(), isNull(), isNull(), any(Pageable.class));
    }

    @Test
    void estadoAjenoNoObtieneCuentaNiModificaDatos() {
        when(cuentas.findByNumeroCuentaAndClienteId(NUMERO, 3)).thenReturn(Optional.empty());

        assertThrows(CuentaNoEncontradaException.class, () -> service.cambiarEstado(NUMERO, false, 7));

        verify(cuentas).findByNumeroCuentaAndClienteId(NUMERO, 3);
        verify(cuentas, never()).findByNumeroCuenta(anyString());
        verifyNoInteractions(clientes, em);
    }

    @Test
    void desactivarCuentaPropiaConservaBloqueoDelCliente() {
        Cuenta cuenta = cuenta(usuario.getCliente());
        when(cuentas.findByNumeroCuentaAndClienteId(NUMERO, 3)).thenReturn(Optional.of(cuenta));
        when(clientes.bloquearPorId(3)).thenReturn(Optional.of(usuario.getCliente()));

        var resultado = service.cambiarEstado(NUMERO, false, 7);

        assertFalse(cuenta.isEstaActiva());
        assertFalse(resultado.estaActiva());
        verify(clientes).bloquearPorId(3);
        verify(em, times(2)).refresh(cuenta);
        verify(em).flush();
    }

    @Test
    void administradorNoPuedeActivarCuentaDeClienteInactivo() {
        usuario.getCliente().setRol(Rol.Administrador);
        Cliente otro = new Cliente();
        otro.setId(9);
        otro.setActivo(false);
        Cuenta cuenta = cuenta(otro);
        cuenta.setEstaActiva(false);
        when(cuentas.findByNumeroCuenta(NUMERO)).thenReturn(Optional.of(cuenta));
        when(clientes.bloquearPorId(9)).thenReturn(Optional.of(otro));

        var exception = assertThrows(NegocioException.class, () -> service.cambiarEstado(NUMERO, true, 7));

        assertEquals(HttpStatus.CONFLICT, exception.getStatus());
        assertFalse(cuenta.isEstaActiva());
        verify(em, never()).flush();
    }

    @Test
    void usuarioInactivoNoConsultaCuentas() {
        usuario.setActivo(false);

        var exception = assertThrows(NegocioException.class,
                () -> service.consultar(new CuentaConsultaRequest(null, null, null, 0, 20), 7));

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatus());
        verifyNoInteractions(cuentas);
    }

    @Test
    void rechazaFiltrosInvalidosAntesDeConsultar() {
        assertThrows(ValidacionException.class,
                () -> service.consultar(new CuentaConsultaRequest(null, null, null, -1, 20), 7));
        assertThrows(ValidacionException.class,
                () -> service.consultar(new CuentaConsultaRequest(null, null, null, 0, 0), 7));
        assertThrows(ValidacionException.class,
                () -> service.consultar(new CuentaConsultaRequest(null, null, null, 0, 101), 7));
        assertThrows(ValidacionException.class,
                () -> service.consultar(new CuentaConsultaRequest(null, BigDecimal.valueOf(-1), null, 0, 20), 7));
        verifyNoInteractions(cuentas);
    }

    private Cuenta cuenta(Cliente propietario) {
        Cuenta cuenta = new Cuenta();
        cuenta.setId(4);
        cuenta.setCliente(propietario);
        cuenta.setNumeroCuenta(NUMERO);
        return cuenta;
    }
}
