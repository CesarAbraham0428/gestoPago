package com.proyecto.servicios.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.proyecto.servicios.entity.sf.Cliente;
import com.proyecto.servicios.entity.sf.Cuenta;
import com.proyecto.servicios.entity.sf.EstadoPago;
import com.proyecto.servicios.entity.sf.Pago;
import com.proyecto.servicios.entity.sf.Rol;
import com.proyecto.servicios.entity.sf.Usuario;
import com.proyecto.servicios.models.request.CuentaConsultaRequest;
import com.proyecto.servicios.models.response.gestopago.GestoPagoPagosResponse;
import com.proyecto.servicios.repositorys.sf.ClientesRepository;
import com.proyecto.servicios.repositorys.sf.CuentaRepository;
import com.proyecto.servicios.repositorys.sf.PagoRepository;
import com.proyecto.servicios.repositorys.sf.UsuarioRepository;
import com.proyecto.servicios.service.exception.ClienteNoEncontradoException;
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

/** Rutas secundarias de CuentaService, PagoRegistroService y AutorizacionService. */
class PagosYCuentasComplementoTest {

    private static final String NUMERO = "000000000000000001";
    private final CuentaRepository cuentas = mock(CuentaRepository.class);
    private final ClientesRepository clientes = mock(ClientesRepository.class);
    private final UsuarioRepository usuarios = mock(UsuarioRepository.class);
    private final PagoRepository pagos = mock(PagoRepository.class);
    private final EntityManager em = mock(EntityManager.class);
    private final AutorizacionService autorizacion = new AutorizacionService(usuarios);
    private final CuentaService cuentaService = new CuentaService(cuentas, clientes, em, autorizacion);
    private final PagoRegistroService registro = new PagoRegistroService(cuentas, pagos, autorizacion,
            new OtpPagoService(mock(TotpService.class), em));
    private Cliente cliente;
    private Usuario usuario;
    private Cuenta cuenta;

    @BeforeEach
    void preparar() {
        cliente = new Cliente();
        cliente.setId(3);
        usuario = new Usuario();
        usuario.setId(7);
        usuario.setCliente(cliente);
        when(usuarios.findById(7)).thenReturn(Optional.of(usuario));
        cuenta = new Cuenta();
        cuenta.setId(4);
        cuenta.setCliente(cliente);
        cuenta.setNumeroCuenta(NUMERO);
        cuenta.setSaldo(new BigDecimal("100.00"));
        when(pagos.findById(55)).thenAnswer(inv -> Optional.of(pagoCon(EstadoPago.PENDIENTE)));
    }

    private Pago pagoCon(EstadoPago estado) {
        Pago pago = new Pago();
        pago.setId(55);
        pago.setCuenta(cuenta);
        pago.setMonto(new BigDecimal("40.00"));
        pago.setEstado(estado);
        return pago;
    }

    private GestoPagoPagosResponse respuesta(String codigo, String original, String legend, String mensaje) {
        GestoPagoPagosResponse respuesta = new GestoPagoPagosResponse();
        respuesta.setCodigo(codigo);
        respuesta.setRespuestaOriginal(original);
        respuesta.setLegend(legend);
        respuesta.setMensaje(mensaje);
        return respuesta;
    }

    // ---------- CuentaService ----------

    @Test
    void consultarRechazaUnSaldoNegativo() {
        CuentaConsultaRequest request = new CuentaConsultaRequest(null, new BigDecimal("-1"), null, 0, 20);

        assertThrows(ValidacionException.class, () -> cuentaService.consultar(request, 7));
    }

    @Test
    void consultarAceptaUnSaldoNuloOPositivo() {
        when(cuentas.consultar(any(), any(), any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        cuentaService.consultar(new CuentaConsultaRequest(true, BigDecimal.TEN, NUMERO, 0, 20), 7);
        cuentaService.consultar(new CuentaConsultaRequest(null, null, null, null, null), 7);

        verify(cuentas, org.mockito.Mockito.times(2)).consultar(any(), any(), any(), any(), any(Pageable.class));
    }

    @Test
    void activarLaCuentaDeUnClienteActivoLaActiva() {
        cuenta.setEstaActiva(false);
        when(cuentas.findByNumeroCuentaAndClienteId(NUMERO, 3)).thenReturn(Optional.of(cuenta));
        when(clientes.bloquearPorId(3)).thenReturn(Optional.of(cliente));

        cuentaService.cambiarEstado(NUMERO, true, 7);

        assertTrue(cuenta.isEstaActiva());
        verify(em).flush();
    }

    @Test
    void cambiarEstadoFallaSiElClienteYaNoExiste() {
        when(cuentas.findByNumeroCuentaAndClienteId(NUMERO, 3)).thenReturn(Optional.of(cuenta));
        when(clientes.bloquearPorId(3)).thenReturn(Optional.empty());

        assertThrows(ClienteNoEncontradoException.class, () -> cuentaService.cambiarEstado(NUMERO, false, 7));
    }

    @Test
    void unAdministradorBuscaLaCuentaSinFiltrarPorCliente() {
        cliente.setRol(Rol.Administrador);
        when(cuentas.findByNumeroCuenta(NUMERO)).thenReturn(Optional.of(cuenta));

        when(clientes.bloquearPorId(3)).thenReturn(Optional.of(cliente));

        cuentaService.cambiarEstado(NUMERO, false, 7);
        when(cuentas.findByNumeroCuenta(NUMERO)).thenReturn(Optional.empty());

        assertThrows(CuentaNoEncontradaException.class, () -> cuentaService.cambiarEstado(NUMERO, false, 7));
    }

    // ---------- PagoRegistroService ----------

    @Test
    void lasConsultasDelHistorialSeDelegaAlRepositorio() {
        Pago pago = pagoCon(EstadoPago.APROBADO);
        when(pagos.findByUpcAndUsuarioId("UPC", 7)).thenReturn(Optional.of(pago));
        when(pagos.findByUsuarioId(any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(pago)));
        Instant ahora = Instant.parse("2026-11-01T10:00:00Z");
        when(pagos.idsPorConfirmar(ahora, ahora, ahora)).thenReturn(List.of(1, 2));

        assertSame(pago, registro.buscarDeUsuario("UPC", 7).orElseThrow());
        assertEquals(1, registro.deUsuario(7, 2, 10).getContent().size());
        verify(pagos).findByUsuarioId(7, PageRequest.of(2, 10,
                org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, "id")));
        assertEquals(List.of(1, 2), registro.idsPorConfirmar(ahora, ahora, ahora));
        assertEquals(55, registro.buscarParaConfirmar(55).getId());
    }

    @Test
    void unPagoInexistenteEsUnErrorDeEstado() {
        when(pagos.findById(99)).thenReturn(Optional.empty());

        assertThrows(IllegalStateException.class, () -> registro.buscarParaConfirmar(99));
    }

    @Test
    void marcarEnRevisionGuardaLaRespuestaYIgnoraLosPagosYaCerrados() {
        Pago pendiente = pagoCon(EstadoPago.PENDIENTE);
        Pago cerrado = pagoCon(EstadoPago.APROBADO);
        when(pagos.findById(55)).thenReturn(Optional.of(pendiente), Optional.of(cerrado));

        registro.marcarEnRevision(55, "x".repeat(300), "<R/>");
        registro.marcarEnRevision(55, "otro", "<R2/>");

        assertEquals(EstadoPago.EN_REVISION, pendiente.getEstado());
        assertEquals(250, pendiente.getMensaje().length());
        assertEquals("<R/>", pendiente.getRespuesta());
        assertEquals(EstadoPago.APROBADO, cerrado.getEstado());
        assertEquals("<R2/>", cerrado.getRespuesta());
    }

    @Test
    void aprobarUsaLaLeyendaOElMensajeDeGestoPago() {
        Pago conLeyenda = pagoCon(EstadoPago.PENDIENTE);
        Pago conMensaje = pagoCon(EstadoPago.PENDIENTE);
        when(pagos.findById(55)).thenReturn(Optional.of(conLeyenda), Optional.of(conMensaje));

        registro.aprobar(55, respuesta("01", "<R/>", "leyenda", "mensaje"));
        registro.aprobar(55, respuesta("01", "<R/>", null, "mensaje"));

        assertEquals("leyenda", conLeyenda.getMensaje());
        assertEquals("mensaje", conMensaje.getMensaje());
    }

    @Test
    void confirmarAplicadoNoReviveUnPagoRechazado() {
        Pago rechazado = pagoCon(EstadoPago.RECHAZADO);
        when(pagos.findById(55)).thenReturn(Optional.of(rechazado));

        registro.confirmarAplicado(55, respuesta("06", "<R/>", null, null));

        assertEquals(EstadoPago.RECHAZADO, rechazado.getEstado());
        assertEquals("<R/>", rechazado.getRespuestaConfirmacion());
    }

    @Test
    void revertirSinRespuestaDevuelveElSaldoYUnPagoCerradoNoSeRevierteDeNuevo() {
        Pago pendiente = pagoCon(EstadoPago.PENDIENTE);
        Pago aprobado = pagoCon(EstadoPago.APROBADO);
        when(pagos.findById(55)).thenReturn(Optional.of(pendiente), Optional.of(aprobado));
        when(cuentas.bloquearPorId(4)).thenReturn(Optional.of(cuenta));

        registro.revertir(55, null, "falló");
        registro.revertir(55, null, "otra vez");

        assertEquals(EstadoPago.RECHAZADO, pendiente.getEstado());
        assertEquals(new BigDecimal("140.00"), cuenta.getSaldo());
        assertNull(pendiente.getCodigoRespuesta());
        assertEquals(EstadoPago.APROBADO, aprobado.getEstado());
    }

    @Test
    void revertirFallaSiLaCuentaDelPagoDesaparecio() {
        when(cuentas.bloquearPorId(4)).thenReturn(Optional.empty());

        assertThrows(CuentaNoEncontradaException.class, () -> registro.revertir(55, null, "x"));
        verify(pagos, never()).saveAndFlush(any());
    }

    // ---------- AutorizacionService ----------

    @Test
    void sinUsuarioAutenticadoSeResponde401() {
        NegocioException error = assertThrows(NegocioException.class, () -> autorizacion.clienteIdParaConsulta(null));

        assertEquals(HttpStatus.UNAUTHORIZED, error.getStatus());
    }

    @Test
    void unUsuarioInactivoOSinClienteNoTienePermisos() {
        usuario.setActivo(false);
        assertEquals(HttpStatus.FORBIDDEN,
                assertThrows(NegocioException.class, () -> autorizacion.clienteIdParaConsulta(7)).getStatus());

        usuario.setActivo(true);
        usuario.setCliente(null);
        assertEquals(HttpStatus.FORBIDDEN,
                assertThrows(NegocioException.class, () -> autorizacion.clienteIdParaConsulta(7)).getStatus());
    }
}
