package com.proyecto.servicios.service.Impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.proyecto.servicios.client.GestoPagoPagoException;
import com.proyecto.servicios.client.GestoPagoPagosClient;
import com.proyecto.servicios.entity.gestopago.GestoPagoToken;
import com.proyecto.servicios.entity.gestopago.Producto;
import com.proyecto.servicios.entity.sf.Cuenta;
import com.proyecto.servicios.entity.sf.Pago;
import com.proyecto.servicios.models.request.PagoServicioRequest;
import com.proyecto.servicios.models.response.PagoServicioResponse;
import com.proyecto.servicios.models.response.gestopago.GestoPagoPagosResponse;
import com.proyecto.servicios.repositorys.gestopago.ProductoRepository;
import com.proyecto.servicios.service.DestinoPago;
import com.proyecto.servicios.service.GestoPagoTokenService;
import com.proyecto.servicios.service.PagoRegistroService;
import com.proyecto.servicios.service.exception.NegocioException;
import com.proyecto.servicios.service.exception.ValidacionException;
import com.proyecto.servicios.utils.FirmaGestoPago;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PagoServiceImplTest {

    private static final String NUMERO = "000000000000000001";
    private static final String LLAVE = "HPo7OLqB4Fkk4E2yGOtwqw8H5fHR9kNx67OR4g4UdlA=";
    private static final String IV = "p5ldmBPdd/9pjC0bDC/nSg==";
    private final ObjectMapper mapper = new ObjectMapper();
    private final ProductoRepository productos = mock(ProductoRepository.class);
    private final PagoRegistroService registro = mock(PagoRegistroService.class);
    private final GestoPagoPagosClient pagosClient = mock(GestoPagoPagosClient.class);
    private final GestoPagoTokenService tokenService = mock(GestoPagoTokenService.class);
    private final FirmaGestoPago firma = spy(new FirmaGestoPago(mapper, LLAVE, IV));
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-19T19:15:26Z"), ZoneId.of("America/Mexico_City"));
    private final PagoServiceImpl service = new PagoServiceImpl(productos, registro, pagosClient, tokenService,
            firma, 10, "DISP", "Sucursal Centro", clock);
    private Pago pago;

    @BeforeEach
    void preparar() {
        Cuenta cuenta = new Cuenta();
        cuenta.setNumeroCuenta(NUMERO);
        pago = new Pago();
        pago.setId(55);
        pago.setCuenta(cuenta);
        when(registro.reservar(any(), any(), any(), anyString(), anyString(), eq(7))).thenAnswer(inv -> {
            pago.setUpc(inv.getArgument(3));
            pago.setPeticion(inv.getArgument(4));
            return pago;
        });
        GestoPagoToken token = new GestoPagoToken();
        token.setToken("tok-1");
        when(tokenService.obtenerTokenActivo(10, "DISP")).thenReturn(Optional.of(token));
    }

    @Test
    void recargaEnviaSoloTelefonoYApruebaConCodigo01() throws Exception {
        producto(1, 2, new BigDecimal("100.00"));
        GestoPagoPagosResponse respuesta = respuesta("01");
        when(pagosClient.enviar(eq("tok-1"), anyString())).thenReturn(respuesta);
        PagoServicioResponse comprobante = mock(PagoServicioResponse.class);
        when(registro.aprobar(55, respuesta)).thenReturn(comprobante);

        assertSame(comprobante, service.pagar(request("5512345678", "REF999"), 7));

        Map<String, Object> peticion = peticionEnviada();
        assertEquals("5512345678", peticion.get("telefono"));
        assertFalse(peticion.containsKey("referencia"), "las recargas no llevan referencia");
        assertFalse(peticion.containsKey("montoPago"), "montoPago es solo para pagos de servicio");
        assertEquals("20261019131526", peticion.get("horaLocal"));
        assertEquals(pago.getUpc(), peticion.get("upc"));
        assertEquals("Sucursal Centro", peticion.get("unidad"));
        verify(registro, never()).revertir(any(), any(), any());
    }

    @Test
    void pagoDeServicioEnviaTelefonoFijoReferenciaYMontoYCobraLaComision() throws Exception {
        // Con tipoFront 2 el precio del catálogo es la comisión: no se compara con el monto, se cobra aparte.
        producto(2, 5, new BigDecimal("10.00"));
        when(pagosClient.enviar(any(), any())).thenReturn(respuesta("01"));

        service.pagar(request(null, "REF999"), 7);

        Map<String, Object> peticion = peticionEnviada();
        assertEquals("1111111111", peticion.get("telefono"));
        assertEquals("REF999", peticion.get("referencia"));
        assertEquals(100.00, ((Number) peticion.get("montoPago")).doubleValue());
        verify(registro).reservar(any(), any(), eq(new DestinoPago("1111111111", "REF999",
                new BigDecimal("100.00"), new BigDecimal("10.00"))), anyString(), anyString(), eq(7));
        verify(pagosClient, never()).verificarReferencia(any(), any());
    }

    @Test
    void pagoDeServicioConConsultaDisponibleVerificaElAdeudoAntesDeCobrar() throws Exception {
        producto(2, 5, null);
        productoDeConsulta();
        when(pagosClient.verificarReferencia(eq("tok-1"), anyString())).thenReturn(adeudo("100.00"));
        when(pagosClient.enviar(any(), any())).thenReturn(respuesta("01"));

        service.pagar(request(null, "REF999"), 7);

        ArgumentCaptor<String> firmada = ArgumentCaptor.forClass(String.class);
        verify(pagosClient).verificarReferencia(eq("tok-1"), firmada.capture());
        Map<?, ?> consulta = mapper.readValue(firma.descifrar(firmada.getValue()), Map.class);
        assertEquals(Map.of("idProducto", 40, "idServicio", 30, "referencia", "REF999"), consulta);
        verify(registro).reservar(any(), any(), any(), anyString(), anyString(), eq(7));
    }

    @Test
    void montoDistintoAlAdeudoSeRechazaSinCobrar() {
        producto(2, 5, null);
        productoDeConsulta();
        when(pagosClient.verificarReferencia(any(), any())).thenReturn(adeudo("250.50"));

        ValidacionException error = assertThrows(ValidacionException.class,
                () -> service.pagar(request(null, "REF999"), 7));

        assertEquals("monto: debe ser igual al adeudo de la referencia (250.50)", error.getMessage());
        verifyNoInteractions(registro);
    }

    @Test
    void referenciaSinAdeudoSeRechazaSinCobrar() {
        producto(2, 5, null);
        productoDeConsulta();
        when(pagosClient.verificarReferencia(any(), any())).thenReturn(adeudo("0"));

        NegocioException error = assertThrows(NegocioException.class,
                () -> service.pagar(request(null, "REF999"), 7));

        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, error.getStatus());
        verifyNoInteractions(registro);
    }

    @Test
    void consultaDeAdeudoFallidaNoCobra() {
        producto(2, 5, null);
        productoDeConsulta();
        when(pagosClient.verificarReferencia(any(), any())).thenReturn(respuesta("15"));

        NegocioException error = assertThrows(NegocioException.class,
                () -> service.pagar(request(null, "REF999"), 7));

        assertEquals("No fue posible verificar la referencia; no se realizó ningún cargo", error.getMessage());
        verifyNoInteractions(registro);
    }

    @Test
    void articuloCanjeableEnviaTelefonoConAlmohadilla() throws Exception {
        producto(1, 10, new BigDecimal("100.00"));
        when(pagosClient.enviar(any(), any())).thenReturn(respuesta("01"));

        service.pagar(request(null, null), 7);

        assertEquals("#1111111111", peticionEnviada().get("telefono"));
    }

    @Test
    void codigo02SaldoDelDistribuidorInsuficienteDevuelveSaldoYResponde503() {
        producto(1, 2, null);
        GestoPagoPagosResponse respuesta = respuesta("02");
        when(pagosClient.enviar(any(), any())).thenReturn(respuesta);

        NegocioException error = assertThrows(NegocioException.class,
                () -> service.pagar(request("5512345678", null), 7));

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, error.getStatus());
        assertEquals("El servicio no está disponible por el momento; no se realizó ningún cargo",
                error.getMessage());
        verify(registro).revertir(eq(55), same(respuesta), any());
        verify(registro, never()).aprobar(any(), any());
    }

    @Test
    void codigo06DuplicadoDevuelveSaldoYResponde409SinTextoDelProveedor() {
        producto(2, 5, null);
        GestoPagoPagosResponse respuesta = respuesta("06");
        respuesta.setMensaje("No es posible pagar la misma factura 2 veces");
        when(pagosClient.enviar(any(), any())).thenReturn(respuesta);

        NegocioException error = assertThrows(NegocioException.class,
                () -> service.pagar(request(null, "REF999"), 7));

        assertEquals(HttpStatus.CONFLICT, error.getStatus());
        assertFalse(error.getMessage().contains("factura"), "el texto de GestoPago no se expone");
        verify(registro).revertir(55, respuesta, "No es posible pagar la misma factura 2 veces");
    }

    @Test
    void rechazoDelProveedorRespondeMensajePropio() {
        producto(1, 2, null);
        GestoPagoPagosResponse respuesta = respuesta("15");
        respuesta.setMensaje("Error interno PR-15 nodo 3");
        when(pagosClient.enviar(any(), any())).thenReturn(respuesta);

        NegocioException error = assertThrows(NegocioException.class,
                () -> service.pagar(request("5512345678", null), 7));

        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, error.getStatus());
        assertEquals("El proveedor rechazó el pago; no se realizó ningún cargo", error.getMessage());
    }

    @Test
    void codigo82DejaElPagoEnRevisionSinDevolverSaldo() {
        producto(1, 2, null);
        GestoPagoPagosResponse respuesta = respuesta("82");
        respuesta.setRespuestaOriginal("<RESPONSE>82</RESPONSE>");
        when(pagosClient.enviar(any(), any())).thenReturn(respuesta);

        NegocioException error = assertThrows(NegocioException.class,
                () -> service.pagar(request("5512345678", null), 7));

        assertEquals(HttpStatus.GATEWAY_TIMEOUT, error.getStatus());
        verify(registro).marcarEnRevision(eq(55), any(), eq("<RESPONSE>82</RESPONSE>"));
        verify(registro, never()).revertir(any(), any(), any());
    }

    @Test
    void timeoutDejaElPagoEnRevisionSinDevolverSaldo() {
        producto(1, 2, null);
        when(pagosClient.enviar(any(), any())).thenThrow(new GestoPagoPagoException("timeout",
                GestoPagoPagoException.Tipo.TIMEOUT, null, true, null));

        NegocioException error = assertThrows(NegocioException.class,
                () -> service.pagar(request("5512345678", null), 7));

        assertEquals(HttpStatus.GATEWAY_TIMEOUT, error.getStatus());
        verify(registro).marcarEnRevision(eq(55), any(), isNull());
        verify(registro, never()).revertir(any(), any(), any());
    }

    @Test
    void sinConexionDevuelveSaldo() {
        producto(1, 2, null);
        when(pagosClient.enviar(any(), any())).thenThrow(new GestoPagoPagoException("sin conexión",
                GestoPagoPagoException.Tipo.COMUNICACION, null, false, null));

        NegocioException error = assertThrows(NegocioException.class,
                () -> service.pagar(request("5512345678", null), 7));

        assertEquals(HttpStatus.BAD_GATEWAY, error.getStatus());
        verify(registro).revertir(eq(55), isNull(), any());
        verify(registro, never()).marcarEnRevision(any(), any(), any());
    }

    @Test
    void tokenVencidoSeRenuevaYSeReintentaUnaVez() {
        producto(1, 2, null);
        GestoPagoToken renovado = new GestoPagoToken();
        renovado.setToken("tok-2");
        when(tokenService.renovarToken()).thenReturn(renovado);
        when(pagosClient.enviar(eq("tok-1"), any())).thenThrow(new GestoPagoPagoException("403",
                GestoPagoPagoException.Tipo.AUTENTICACION, 403, false, null));
        GestoPagoPagosResponse respuesta = respuesta("01");
        when(pagosClient.enviar(eq("tok-2"), any())).thenReturn(respuesta);

        service.pagar(request("5512345678", null), 7);

        verify(registro).aprobar(55, respuesta);
    }

    @Test
    void falloAntesDeEnviarDevuelveSaldo() {
        producto(1, 2, null);
        doThrow(new IllegalStateException("sin llave")).when(firma).firmarJson(anyString());

        assertThrows(IllegalStateException.class, () -> service.pagar(request("5512345678", null), 7));

        verify(registro).revertir(eq(55), isNull(), any());
        verifyNoInteractions(pagosClient);
    }

    @Test
    void servicioRequiereReferencia() {
        producto(2, 5, null);

        ValidacionException error = assertThrows(ValidacionException.class,
                () -> service.pagar(request("5512345678", null), 7));

        assertTrue(error.getMessage().startsWith("referencia"));
        verifyNoInteractions(registro);
    }

    @Test
    void recargaRequiereTelefono() {
        producto(1, 2, null);

        assertThrows(ValidacionException.class, () -> service.pagar(request(null, "REF999"), 7));
        verifyNoInteractions(registro);
    }

    @Test
    void recargaConPrecioFijoRechazaOtroMonto() {
        producto(1, 2, new BigDecimal("50.00"));

        assertThrows(ValidacionException.class, () -> service.pagar(request("5512345678", null), 7));
        verifyNoInteractions(registro);
    }

    @Test
    void productoDeConsultaNoSePuedePagar() {
        producto(4, 5, null);

        assertThrows(ValidacionException.class, () -> service.pagar(request(null, "REF999"), 7));
        verifyNoInteractions(registro);
    }

    @Test
    void productoInexistenteResponde404() {
        when(productos.findFirstByIdProductoAndIdServicio(any(), any())).thenReturn(Optional.empty());

        NegocioException error = assertThrows(NegocioException.class,
                () -> service.pagar(request("5512345678", null), 7));
        assertEquals(HttpStatus.NOT_FOUND, error.getStatus());
    }

    @Test
    void conciliacionReenviaLaMismaPeticionAConfirmTx() {
        pagoPorConfirmar(80, "{\"upc\":\"ABC\"}");
        GestoPagoPagosResponse aplicada = respuesta("06");
        aplicada.setNumAutorizacion("100024988");
        String firmada = firma.firmarJson("{\"upc\":\"ABC\"}");
        when(pagosClient.confirmar("tok-1", firmada)).thenReturn(aplicada);

        service.conciliarPendientes();

        verify(registro).idsPorConfirmar(clock.instant().minusSeconds(24 * 3600),
                clock.instant().minusSeconds(62), clock.instant().minusSeconds(300));
        verify(registro).confirmarAplicado(80, aplicada);
    }

    @Test
    void conciliacionDevuelveSaldoSiConfirmTxRespondeNoAplicada() {
        pagoPorConfirmar(81, "{\"upc\":\"DEF\"}");
        GestoPagoPagosResponse noAplicada = respuesta("70");
        noAplicada.setNumAutorizacion("0");
        when(pagosClient.confirmar(any(), any())).thenReturn(noAplicada);

        service.conciliarPendientes();

        verify(registro).confirmarNoAplicado(81, noAplicada);
        verify(registro, never()).confirmarAplicado(any(), any());
    }

    @Test
    void conciliacionContinuaSiUnPagoFalla() {
        pagoPorConfirmar(82, "{\"upc\":\"G\"}");
        when(pagosClient.confirmar(any(), any())).thenThrow(new GestoPagoPagoException("timeout",
                GestoPagoPagoException.Tipo.TIMEOUT, null, true, null));

        assertDoesNotThrow(service::conciliarPendientes);
        verify(registro, never()).confirmarAplicado(any(), any());
        verify(registro, never()).confirmarNoAplicado(any(), any());
    }

    private void pagoPorConfirmar(int id, String peticion) {
        Pago pendiente = new Pago();
        pendiente.setId(id);
        pendiente.setPeticion(peticion);
        when(registro.idsPorConfirmar(any(), any(), any())).thenReturn(List.of(id));
        when(registro.buscarParaConfirmar(id)).thenReturn(pendiente);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> peticionEnviada() throws Exception {
        ArgumentCaptor<String> firmada = ArgumentCaptor.forClass(String.class);
        verify(pagosClient, atLeastOnce()).enviar(any(), firmada.capture());
        return mapper.readValue(firma.descifrar(firmada.getValue()), Map.class);
    }

    private void producto(int tipoFront, int idCatTipoServicio, BigDecimal precio) {
        Producto producto = new Producto();
        producto.setIdProducto(20);
        producto.setIdServicio(30);
        producto.setTipoFront(tipoFront);
        producto.setIdCatTipoServicio(idCatTipoServicio);
        producto.setPrecio(precio);
        when(productos.findFirstByIdProductoAndIdServicio(20, 30)).thenReturn(Optional.of(producto));
    }

    private void productoDeConsulta() {
        Producto consulta = new Producto();
        consulta.setIdProducto(40);
        consulta.setIdServicio(30);
        consulta.setTipoFront(4);
        when(productos.findFirstByIdServicioAndTipoFront(30, 4)).thenReturn(Optional.of(consulta));
    }

    private GestoPagoPagosResponse adeudo(String saldo) {
        GestoPagoPagosResponse respuesta = respuesta("01");
        respuesta.setSaldo(new BigDecimal(saldo));
        return respuesta;
    }

    private PagoServicioRequest request(String telefono, String referencia) {
        return new PagoServicioRequest(NUMERO, 20, 30, new BigDecimal("100.00"), telefono, referencia, "1234");
    }

    private GestoPagoPagosResponse respuesta(String codigo) {
        GestoPagoPagosResponse respuesta = new GestoPagoPagosResponse();
        respuesta.setCodigo(codigo);
        respuesta.setIdTx(900L);
        return respuesta;
    }
}
