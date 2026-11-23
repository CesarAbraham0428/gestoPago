package com.proyecto.servicios.service.Impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.proyecto.servicios.client.GestoPagoAuthException;
import com.proyecto.servicios.client.GestoPagoPagoException;
import com.proyecto.servicios.entity.gestopago.Producto;
import com.proyecto.servicios.entity.sf.Cuenta;
import com.proyecto.servicios.entity.sf.EstadoPago;
import com.proyecto.servicios.entity.sf.Pago;
import com.proyecto.servicios.models.request.PagoServicioRequest;
import com.proyecto.servicios.models.response.ConsultaPagoResponse;
import com.proyecto.servicios.models.response.PagoServicioResponse;
import com.proyecto.servicios.models.response.gestopago.GestoPagoPagosResponse;
import com.proyecto.servicios.repositorys.gestopago.ProductoRepository;
import com.proyecto.servicios.service.DestinoPago;
import com.proyecto.servicios.service.GestoPagoPagosGateway;
import com.proyecto.servicios.service.PagoRegistroService;
import com.proyecto.servicios.service.ResolutorDestinoPago;
import com.proyecto.servicios.service.exception.NegocioException;
import com.proyecto.servicios.service.exception.ValidacionException;
import com.proyecto.servicios.utils.FirmaGestoPago;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpStatus;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Orquestación del pago: la resolución del destino, el token y la conciliación se prueban en sus propias clases. */
class PagoServiceImplTest {

    private static final String NUMERO = "000000000000000001";
    private static final String LLAVE = "HPo7OLqB4Fkk4E2yGOtwqw8H5fHR9kNx67OR4g4UdlA=";
    private static final String IV = "p5ldmBPdd/9pjC0bDC/nSg==";
    private static final DestinoPago RECARGA = new DestinoPago("5512345678", null, null, BigDecimal.ZERO);
    private static final DestinoPago SERVICIO = new DestinoPago("1111111111", "REF999",
            new BigDecimal("100.00"), new BigDecimal("10.00"));
    private final ObjectMapper mapper = new ObjectMapper();
    private final ProductoRepository productos = mock(ProductoRepository.class);
    private final PagoRegistroService registro = mock(PagoRegistroService.class);
    private final GestoPagoPagosGateway gateway = mock(GestoPagoPagosGateway.class);
    private final ResolutorDestinoPago resolutor = mock(ResolutorDestinoPago.class);
    private final FirmaGestoPago firma = spy(new FirmaGestoPago(mapper, LLAVE, IV));
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-19T19:15:26Z"), ZoneId.of("America/Mexico_City"));
    private final PagoServiceImpl service = new PagoServiceImpl(productos, registro, gateway, resolutor, firma,
            "Sucursal Centro", clock);
    private Pago pago;

    @BeforeEach
    void preparar() {
        Cuenta cuenta = new Cuenta();
        cuenta.setNumeroCuenta(NUMERO);
        pago = new Pago();
        pago.setId(55);
        pago.setCuenta(cuenta);
        Producto producto = new Producto();
        producto.setIdProducto(20);
        producto.setIdServicio(30);
        when(productos.findFirstByIdProductoAndIdServicio(20, 30)).thenReturn(Optional.of(producto));
        when(resolutor.resolver(any(), any())).thenReturn(RECARGA);
        when(registro.reservar(any(), any(), any(), anyString(), anyString(), eq(7))).thenAnswer(inv -> {
            pago.setUpc(inv.getArgument(3));
            pago.setPeticion(inv.getArgument(4));
            return pago;
        });
    }

    @Test
    void recargaEnviaSoloTelefonoYApruebaConCodigo01() throws Exception {
        GestoPagoPagosResponse respuesta = respuesta("01");
        when(gateway.enviar(anyString())).thenReturn(respuesta);
        PagoServicioResponse comprobante = mock(PagoServicioResponse.class);
        when(registro.aprobar(55, respuesta)).thenReturn(comprobante);

        assertSame(comprobante, service.pagar(request(), 7));

        Map<String, Object> peticion = peticionEnviada();
        assertEquals("5512345678", peticion.get("telefono"));
        assertFalse(peticion.containsKey("referencia"), "las recargas no llevan referencia");
        assertFalse(peticion.containsKey("montoPago"), "montoPago es solo para pagos de servicio");
        assertEquals("20261019131526", peticion.get("horaLocal"));
        assertEquals(pago.getUpc(), peticion.get("upc"));
        assertEquals("Sucursal Centro", peticion.get("unidad"));
        assertEquals(15, pago.getUpc().length());
        verify(registro, never()).revertir(any(), any(), any());
    }

    @Test
    void pagoDeServicioEnviaReferenciaYMontoYReservaMontoMasComision() throws Exception {
        when(resolutor.resolver(any(), any())).thenReturn(SERVICIO);
        when(gateway.enviar(anyString())).thenReturn(respuesta("01"));

        service.pagar(request(), 7);

        Map<String, Object> peticion = peticionEnviada();
        assertEquals("1111111111", peticion.get("telefono"));
        assertEquals("REF999", peticion.get("referencia"));
        assertEquals(100.00, ((Number) peticion.get("montoPago")).doubleValue());
        verify(registro).reservar(any(), any(), eq(SERVICIO), anyString(), anyString(), eq(7));
    }

    @Test
    void laPeticionGuardadaEsLaMismaQueSeEnvia() {
        when(gateway.enviar(anyString())).thenReturn(respuesta("01"));

        service.pagar(request(), 7);

        ArgumentCaptor<String> reservada = ArgumentCaptor.forClass(String.class);
        verify(registro).reservar(any(), any(), any(), anyString(), reservada.capture(), eq(7));
        verify(gateway).enviar(firma.firmarJson(reservada.getValue()));
    }

    @Test
    void codigo02SaldoDelDistribuidorInsuficienteDevuelveSaldoYResponde503() {
        GestoPagoPagosResponse respuesta = respuesta("02");
        when(gateway.enviar(anyString())).thenReturn(respuesta);

        NegocioException error = assertThrows(NegocioException.class, () -> service.pagar(request(), 7));

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, error.getStatus());
        assertEquals("El servicio no está disponible por el momento; no se realizó ningún cargo",
                error.getMessage());
        verify(registro).revertir(eq(55), same(respuesta), any());
        verify(registro, never()).aprobar(any(), any());
    }

    @Test
    void codigo06DuplicadoDevuelveSaldoYResponde409SinTextoDelProveedor() {
        GestoPagoPagosResponse respuesta = respuesta("06");
        respuesta.setMensaje("No es posible pagar la misma factura 2 veces");
        when(gateway.enviar(anyString())).thenReturn(respuesta);

        NegocioException error = assertThrows(NegocioException.class, () -> service.pagar(request(), 7));

        assertEquals(HttpStatus.CONFLICT, error.getStatus());
        assertFalse(error.getMessage().contains("factura"), "el texto de GestoPago no se expone");
        verify(registro).revertir(55, respuesta, "No es posible pagar la misma factura 2 veces");
    }

    @Test
    void rechazoDelProveedorRespondeMensajePropioYGuardaElTextoDelProveedor() {
        GestoPagoPagosResponse respuesta = respuesta("15");
        respuesta.setMensaje("Error interno PR-15 nodo 3");
        when(gateway.enviar(anyString())).thenReturn(respuesta);

        NegocioException error = assertThrows(NegocioException.class, () -> service.pagar(request(), 7));

        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, error.getStatus());
        assertEquals("El proveedor rechazó el pago; no se realizó ningún cargo", error.getMessage());
        verify(registro).revertir(55, respuesta, "Error interno PR-15 nodo 3");
    }

    @Test
    void codigo82DejaElPagoEnRevisionSinDevolverSaldo() {
        GestoPagoPagosResponse respuesta = respuesta("82");
        respuesta.setRespuestaOriginal("<RESPONSE>82</RESPONSE>");
        when(gateway.enviar(anyString())).thenReturn(respuesta);

        NegocioException error = assertThrows(NegocioException.class, () -> service.pagar(request(), 7));

        assertEquals(HttpStatus.GATEWAY_TIMEOUT, error.getStatus());
        assertTrue(error.getMessage().contains(pago.getUpc()), "el cliente recibe el UPC para consultar el pago");
        verify(registro).marcarEnRevision(eq(55), any(), eq("<RESPONSE>82</RESPONSE>"));
        verify(registro, never()).revertir(any(), any(), any());
    }

    @Test
    void timeoutDejaElPagoEnRevisionSinDevolverSaldo() {
        when(gateway.enviar(anyString())).thenThrow(new GestoPagoPagoException("timeout",
                GestoPagoPagoException.Tipo.TIMEOUT, null, true, null));

        NegocioException error = assertThrows(NegocioException.class, () -> service.pagar(request(), 7));

        assertEquals(HttpStatus.GATEWAY_TIMEOUT, error.getStatus());
        verify(registro).marcarEnRevision(eq(55), any(), isNull());
        verify(registro, never()).revertir(any(), any(), any());
    }

    @Test
    void sinConexionDevuelveSaldo() {
        when(gateway.enviar(anyString())).thenThrow(new GestoPagoPagoException("sin conexión",
                GestoPagoPagoException.Tipo.COMUNICACION, null, false, null));

        NegocioException error = assertThrows(NegocioException.class, () -> service.pagar(request(), 7));

        assertEquals(HttpStatus.BAD_GATEWAY, error.getStatus());
        verify(registro).revertir(eq(55), isNull(), any());
        verify(registro, never()).marcarEnRevision(any(), any(), any());
    }

    @Test
    void falloDeAutenticacionConGestoPagoDevuelveSaldoYRelanzaLaExcepcion() {
        when(gateway.enviar(anyString())).thenThrow(new GestoPagoAuthException("No hay un token activo",
                GestoPagoAuthException.Tipo.RESPUESTA_INVALIDA, null, null));

        assertThrows(GestoPagoAuthException.class, () -> service.pagar(request(), 7));

        verify(registro).revertir(eq(55), isNull(), any());
        verify(registro, never()).marcarEnRevision(any(), any(), any());
    }

    @Test
    void falloAntesDeEnviarDevuelveSaldo() {
        doThrow(new IllegalStateException("sin llave")).when(firma).firmarJson(anyString());

        assertThrows(IllegalStateException.class, () -> service.pagar(request(), 7));

        verify(registro).revertir(eq(55), isNull(), any());
        verifyNoInteractions(gateway);
    }

    @Test
    void siFallaGuardarLaAprobacionSeResponde200ConElComprobanteParaQueNoSeRepita() {
        GestoPagoPagosResponse respuesta = respuesta("01");
        when(gateway.enviar(anyString())).thenReturn(respuesta);
        when(registro.aprobar(55, respuesta)).thenThrow(new DataAccessResourceFailureException("BD caída"));
        PagoServicioResponse comprobante = mock(PagoServicioResponse.class);
        when(registro.comprobante(pago, respuesta, null)).thenReturn(comprobante);

        assertSame(comprobante, service.pagar(request(), 7));

        verify(registro, never()).revertir(any(), any(), any());
    }

    @Test
    void siElDestinoNoEsValidoNoSeReservaNiSeEnvia() {
        when(resolutor.resolver(any(), any())).thenThrow(new ValidacionException("referencia: obligatoria"));

        assertThrows(ValidacionException.class, () -> service.pagar(request(), 7));

        verifyNoInteractions(registro, gateway);
    }

    @Test
    void productoInexistenteResponde404SinReservar() {
        when(productos.findFirstByIdProductoAndIdServicio(any(), any())).thenReturn(Optional.empty());

        NegocioException error = assertThrows(NegocioException.class, () -> service.pagar(request(), 7));

        assertEquals(HttpStatus.NOT_FOUND, error.getStatus());
        verifyNoInteractions(resolutor, registro, gateway);
    }

    @Test
    void laUnidadDebeMedirDeTresAVeinticincoCaracteres() {
        assertThrows(IllegalStateException.class, () -> new PagoServiceImpl(productos, registro, gateway,
                resolutor, firma, "ab", clock));
        assertThrows(IllegalStateException.class, () -> new PagoServiceImpl(productos, registro, gateway,
                resolutor, firma, "x".repeat(26), clock));
        assertThrows(IllegalStateException.class, () -> new PagoServiceImpl(productos, registro, gateway,
                resolutor, firma, null, clock));
    }

    @Test
    void consultaDePagoAprobadoDevuelveEstadoYAutorizacionSinLlamarAGestoPago() {
        Pago aprobado = pagoConsultable(EstadoPago.APROBADO);
        aprobado.setIdTx(99L);
        aprobado.setNumAutorizacion("AUT1");
        when(registro.buscarDeUsuario("ABCDEFGHIJ12345", 7)).thenReturn(Optional.of(aprobado));

        ConsultaPagoResponse respuesta = service.consultar("ABCDEFGHIJ12345", 7);

        assertEquals("APROBADO", respuesta.estado());
        assertEquals(99L, respuesta.idTx());
        assertEquals("AUT1", respuesta.numAutorizacion());
        verifyNoInteractions(gateway);
    }

    @Test
    void consultaDePagoEnRevisionNoExponeAutorizacion() {
        when(registro.buscarDeUsuario("ABCDEFGHIJ12345", 7))
                .thenReturn(Optional.of(pagoConsultable(EstadoPago.EN_REVISION)));

        ConsultaPagoResponse respuesta = service.consultar("ABCDEFGHIJ12345", 7);

        assertEquals("EN_REVISION", respuesta.estado());
        assertNull(respuesta.idTx());
        assertNull(respuesta.numAutorizacion());
    }

    @Test
    void consultaDePagoRechazadoIndicaQueNoHuboCargo() {
        when(registro.buscarDeUsuario("ABCDEFGHIJ12345", 7))
                .thenReturn(Optional.of(pagoConsultable(EstadoPago.RECHAZADO)));

        ConsultaPagoResponse respuesta = service.consultar("ABCDEFGHIJ12345", 7);

        assertEquals("RECHAZADO", respuesta.estado());
        assertTrue(respuesta.mensaje().contains("no se realizó ningún cargo"));
    }

    @Test
    void consultaDePagoAjenoOInexistenteResponde404() {
        when(registro.buscarDeUsuario("ABCDEFGHIJ12345", 7)).thenReturn(Optional.empty());

        NegocioException error = assertThrows(NegocioException.class,
                () -> service.consultar("ABCDEFGHIJ12345", 7));
        assertEquals(HttpStatus.NOT_FOUND, error.getStatus());
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> peticionEnviada() throws Exception {
        ArgumentCaptor<String> firmada = ArgumentCaptor.forClass(String.class);
        verify(gateway).enviar(firmada.capture());
        return mapper.readValue(firma.descifrar(firmada.getValue()), Map.class);
    }

    private Pago pagoConsultable(EstadoPago estado) {
        Pago consultable = new Pago();
        consultable.setUpc("ABCDEFGHIJ12345");
        consultable.setEstado(estado);
        consultable.setMonto(new BigDecimal("100.00"));
        consultable.setComisionCliente(BigDecimal.ZERO);
        return consultable;
    }

    private PagoServicioRequest request() {
        return new PagoServicioRequest(NUMERO, 20, 30, new BigDecimal("100.00"), "5512345678", null, "1234");
    }

    private GestoPagoPagosResponse respuesta(String codigo) {
        GestoPagoPagosResponse respuesta = new GestoPagoPagosResponse();
        respuesta.setCodigo(codigo);
        respuesta.setIdTx(900L);
        return respuesta;
    }
}
