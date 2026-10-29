package com.proyecto.servicios.service;

import com.proyecto.servicios.entity.gestopago.Producto;
import com.proyecto.servicios.entity.sf.Cliente;
import com.proyecto.servicios.entity.sf.Cuenta;
import com.proyecto.servicios.entity.sf.EstadoPago;
import com.proyecto.servicios.entity.sf.Pago;
import com.proyecto.servicios.entity.sf.Usuario;
import com.proyecto.servicios.models.request.PagoServicioRequest;
import com.proyecto.servicios.models.response.gestopago.GestoPagoPagosResponse;
import com.proyecto.servicios.repositorys.sf.CuentaRepository;
import com.proyecto.servicios.repositorys.sf.PagoRepository;
import com.proyecto.servicios.repositorys.sf.UsuarioRepository;
import com.proyecto.servicios.service.exception.NegocioException;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PagoRegistroServiceTest {

    private static final String NUMERO = "000000000000000001";
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(4);
    private final CuentaRepository cuentas = mock(CuentaRepository.class);
    private final PagoRepository pagos = mock(PagoRepository.class);
    private final UsuarioRepository usuarios = mock(UsuarioRepository.class);
    private final PagoRegistroService service = new PagoRegistroService(cuentas, pagos,
            new AutorizacionService(usuarios), new PinAutorizacionService(encoder));
    private final DestinoPago recarga = new DestinoPago("5512345678", null, null, BigDecimal.ZERO);
    private Cuenta cuenta;
    private Producto producto;

    @BeforeEach
    void preparar() {
        Cliente cliente = new Cliente();
        cliente.setId(3);
        Usuario usuario = new Usuario();
        usuario.setId(7);
        usuario.setCliente(cliente);
        when(usuarios.findById(7)).thenReturn(Optional.of(usuario));
        cuenta = new Cuenta();
        cuenta.setId(4);
        cuenta.setCliente(cliente);
        cuenta.setNumeroCuenta(NUMERO);
        cuenta.setSaldo(new BigDecimal("150.00"));
        cuenta.setPinHash(encoder.encode("1234"));
        when(cuentas.bloquearPorNumero(NUMERO)).thenReturn(Optional.of(cuenta));
        when(cuentas.bloquearPorId(4)).thenReturn(Optional.of(cuenta));
        when(pagos.saveAndFlush(any(Pago.class))).thenAnswer(inv -> inv.getArgument(0));
        producto = new Producto();
        producto.setIdProducto(20);
        producto.setIdServicio(30);
        producto.setTipoFront(1);
    }

    @Test
    void reservarDescuentaSaldoYRegistraPagoPendiente() {
        Pago pago = service.reservar(request("100.00", "1234"), producto, recarga, "UPC1234567",
                "{\"upc\":\"UPC1234567\"}", 7);

        assertEquals(new BigDecimal("50.00"), cuenta.getSaldo());
        assertEquals(EstadoPago.PENDIENTE, pago.getEstado());
        assertEquals("5512345678", pago.getTelefono());
        assertEquals("UPC1234567", pago.getUpc());
        assertEquals("{\"upc\":\"UPC1234567\"}", pago.getPeticion());
    }

    @Test
    void cuentaInactivaNoPuedePagar() {
        cuenta.setEstaActiva(false);
        assertEstado(HttpStatus.CONFLICT, request("10.00", "1234"));
    }

    @Test
    void cuentaSinSaldoNoPuedePagar() {
        cuenta.setSaldo(BigDecimal.ZERO);
        assertEstado(HttpStatus.UNPROCESSABLE_ENTITY, request("10.00", "1234"));
    }

    @Test
    void saldoMenorAlMontoNoPuedePagar() {
        assertEstado(HttpStatus.UNPROCESSABLE_ENTITY, request("150.01", "1234"));
    }

    @Test
    void saldoIgualAlMontoSiPuedePagar() {
        service.reservar(request("150.00", "1234"), producto, recarga, "UPC1234567", "{}", 7);
        assertEquals(0, cuenta.getSaldo().signum());
    }

    @Test
    void pagoDeServicioDescuentaMontoMasComision() {
        DestinoPago servicio = new DestinoPago("1111111111", "REF1", new BigDecimal("100.00"), new BigDecimal("12.00"));

        Pago pago = service.reservar(request("100.00", "1234"), producto, servicio, "UPC1234567", "{}", 7);

        assertEquals(new BigDecimal("38.00"), cuenta.getSaldo());
        assertEquals(new BigDecimal("12.00"), pago.getComisionCliente());
    }

    @Test
    void saldoQueNoCubreLaComisionNoPuedePagar() {
        DestinoPago servicio = new DestinoPago("1111111111", "REF1", new BigDecimal("150.00"), BigDecimal.ONE);
        NegocioException error = assertThrows(NegocioException.class, () ->
                service.reservar(request("150.00", "1234"), producto, servicio, "UPC1234567", "{}", 7));
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, error.getStatus());
    }

    @Test
    void pinIncorrectoNoDescuentaSaldoYCuentaElIntento() {
        assertEstado(HttpStatus.FORBIDDEN, request("10.00", "9999"));
        assertEquals(new BigDecimal("150.00"), cuenta.getSaldo());
        assertEquals(1, cuenta.getIntentosPinFallidos());
    }

    @Test
    void cuentaSinPinConfiguradoNoPuedePagar() {
        cuenta.setPinHash(null);
        assertEstado(HttpStatus.CONFLICT, request("10.00", "1234"));
    }

    @Test
    void cuentaDeOtroClienteEsRechazada() {
        Cliente otro = new Cliente();
        otro.setId(99);
        cuenta.setCliente(otro);
        assertEstado(HttpStatus.FORBIDDEN, request("10.00", "1234"));
    }

    @Test
    void revertirDevuelveMontoYComision() {
        Pago pago = pagoPendiente(EstadoPago.PENDIENTE);
        pago.setComisionCliente(new BigDecimal("12.00"));

        service.revertir(55, null, "No enviado");

        assertEquals(new BigDecimal("202.00"), cuenta.getSaldo());
    }

    @Test
    void revertirDevuelveSaldoUnaSolaVezYGuardaLaRespuesta() {
        Pago pago = pagoPendiente(EstadoPago.PENDIENTE);
        GestoPagoPagosResponse respuesta = respuesta("02", "<RESPONSE>02</RESPONSE>");

        service.revertir(55, respuesta, "Saldo insuficiente");
        service.revertir(55, respuesta, "Saldo insuficiente");

        assertEquals(new BigDecimal("190.00"), cuenta.getSaldo());
        assertEquals(EstadoPago.RECHAZADO, pago.getEstado());
        assertEquals("02", pago.getCodigoRespuesta());
        assertEquals("<RESPONSE>02</RESPONSE>", pago.getRespuesta());
    }

    @Test
    void aprobarGuardaEvidenciaYDevuelveElComprobanteDeGestoPago() {
        Pago pago = pagoPendiente(EstadoPago.PENDIENTE);
        pago.setTelefono("1111111111");
        pago.setReferencia("REF1");
        GestoPagoPagosResponse respuesta = respuesta("01", "<RESPONSE>01</RESPONSE>");
        respuesta.setIdTx(291437188L);
        respuesta.setNumAutorizacion("100024988");

        var comprobante = service.aprobar(55, respuesta);

        assertEquals(EstadoPago.APROBADO, pago.getEstado());
        assertEquals(291437188L, pago.getIdTx());
        assertEquals("<RESPONSE>01</RESPONSE>", pago.getRespuesta());
        assertEquals("APROBADO", comprobante.estado());
        assertEquals(291437188L, comprobante.idTx());
        assertEquals("100024988", comprobante.numAutorizacion());
        assertEquals("REF1", comprobante.referencia());
        assertEquals(new BigDecimal("150.00"), comprobante.saldoDisponible());
    }

    @Test
    void confirmTxAplicadoApruebaSinTocarElSaldo() {
        Pago pago = pagoPendiente(EstadoPago.EN_REVISION);
        GestoPagoPagosResponse respuesta = respuesta("06", "<RESPONSE>06</RESPONSE>");
        respuesta.setNumAutorizacion("100024988");

        service.confirmarAplicado(55, respuesta);

        assertEquals(EstadoPago.APROBADO, pago.getEstado());
        assertEquals("<RESPONSE>06</RESPONSE>", pago.getRespuestaConfirmacion());
        assertEquals(new BigDecimal("150.00"), cuenta.getSaldo());
    }

    @Test
    void confirmTxNoAplicadoDevuelveSaldo() {
        Pago pago = pagoPendiente(EstadoPago.EN_REVISION);

        service.confirmarNoAplicado(55, respuesta("70", "<RESPONSE>70</RESPONSE>"));

        assertEquals(EstadoPago.RECHAZADO, pago.getEstado());
        assertEquals(new BigDecimal("190.00"), cuenta.getSaldo());
        assertEquals("<RESPONSE>70</RESPONSE>", pago.getRespuestaConfirmacion());
    }

    @Test
    void marcarEnRevisionSoloAplicaAPagosPendientes() {
        Pago pago = pagoPendiente(EstadoPago.PENDIENTE);

        service.marcarEnRevision(55, "timeout", null);

        assertEquals(EstadoPago.EN_REVISION, pago.getEstado());
        assertEquals(new BigDecimal("150.00"), cuenta.getSaldo());
    }

    private Pago pagoPendiente(EstadoPago estado) {
        Pago pago = new Pago();
        pago.setId(55);
        pago.setCuenta(cuenta);
        pago.setMonto(new BigDecimal("40.00"));
        pago.setEstado(estado);
        when(pagos.findById(55)).thenReturn(Optional.of(pago));
        return pago;
    }

    private GestoPagoPagosResponse respuesta(String codigo, String original) {
        GestoPagoPagosResponse respuesta = new GestoPagoPagosResponse();
        respuesta.setCodigo(codigo);
        respuesta.setRespuestaOriginal(original);
        return respuesta;
    }

    private void assertEstado(HttpStatus estado, PagoServicioRequest request) {
        NegocioException error = assertThrows(NegocioException.class,
                () -> service.reservar(request, producto, recarga, "UPC1234567", "{}", 7));
        assertEquals(estado, error.getStatus());
        verify(pagos, never()).saveAndFlush(any());
    }

    private PagoServicioRequest request(String monto, String pin) {
        return new PagoServicioRequest(NUMERO, 20, 30, new BigDecimal(monto), "5512345678", null, pin);
    }
}
