package com.proyecto.servicios.service;

import com.proyecto.servicios.entity.gestopago.Producto;
import com.proyecto.servicios.models.request.PagoServicioRequest;
import com.proyecto.servicios.service.exception.NegocioException;
import com.proyecto.servicios.service.exception.ValidacionException;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ResolutorDestinoPagoTest {

    private final VerificadorAdeudo adeudo = mock(VerificadorAdeudo.class);
    private final ResolutorDestinoPago resolutor = new ResolutorDestinoPago(adeudo);

    @Test
    void recargaUsaElTelefonoDelClienteSinReferenciaNiMontoPago() {
        DestinoPago destino = resolutor.resolver(producto(1, 2, "100.00"), request("100.00", "5512345678", "REF999"));

        assertEquals(new DestinoPago("5512345678", null, null, BigDecimal.ZERO), destino);
        verifyNoInteractions(adeudo);
    }

    @Test
    void recargaRequiereTelefono() {
        ValidacionException error = assertThrows(ValidacionException.class,
                () -> resolutor.resolver(producto(1, 2, "100.00"), request("100.00", null, "REF999")));

        assertTrue(error.getMessage().startsWith("telefono"));
    }

    @Test
    void recargaConPrecioFijoRechazaOtroMonto() {
        ValidacionException error = assertThrows(ValidacionException.class,
                () -> resolutor.resolver(producto(1, 2, "50.00"), request("100.00", "5512345678", null)));

        assertEquals("monto: el producto tiene un precio fijo de 50.00", error.getMessage());
    }

    @Test
    void recargaSinPrecioPositivoEnElCatalogoSeRechaza() {
        for (String precio : new String[] {null, "0", "0.00", "-5.00"}) {
            ValidacionException error = assertThrows(ValidacionException.class,
                    () -> resolutor.resolver(producto(1, 2, precio), request("100.00", "5512345678", null)));

            assertEquals("idProducto: este producto no se puede pagar", error.getMessage());
        }
    }

    @Test
    void articuloCanjeableUsaTelefonoConAlmohadillaSinPedirTelefono() {
        for (int tipoServicio : new int[] {10, 11}) {
            DestinoPago destino = resolutor.resolver(producto(1, tipoServicio, "100.00"),
                    request("100.00", null, null));

            assertEquals(new DestinoPago("#1111111111", null, null, BigDecimal.ZERO), destino);
        }
    }

    @Test
    void articuloCanjeableSinPrecioEnElCatalogoSeRechaza() {
        assertThrows(ValidacionException.class,
                () -> resolutor.resolver(producto(1, 10, null), request("100.00", null, null)));
    }

    @Test
    void pagoDeServicioUsaTelefonoFijoReferenciaMontoYCobraElPrecioComoComision() {
        Producto producto = producto(2, 5, "10.00");
        PagoServicioRequest request = request("100.00", null, "REF999");

        DestinoPago destino = resolutor.resolver(producto, request);

        assertEquals(new DestinoPago("1111111111", "REF999", new BigDecimal("100.00"), new BigDecimal("10.00")),
                destino);
        verify(adeudo).validar(producto, request);
    }

    @Test
    void pagoDeServicioSinPrecioNoCobraComision() {
        DestinoPago destino = resolutor.resolver(producto(2, 5, null), request("100.00", null, "REF999"));

        assertEquals(BigDecimal.ZERO, destino.comision());
    }

    @Test
    void pagoDeServicioNuncaCobraUnaComisionNegativa() {
        DestinoPago destino = resolutor.resolver(producto(2, 5, "-3.00"), request("100.00", null, "REF999"));

        assertEquals(0, destino.comision().signum());
    }

    @Test
    void pagoDeServicioCanjeableUsaTelefonoConAlmohadilla() {
        DestinoPago destino = resolutor.resolver(producto(2, 11, null), request("100.00", null, "REF999"));

        assertEquals("#1111111111", destino.telefono());
    }

    @Test
    void pagoDeServicioRequiereReferenciaYNoConsultaElAdeudo() {
        ValidacionException error = assertThrows(ValidacionException.class,
                () -> resolutor.resolver(producto(2, 5, null), request("100.00", "5512345678", null)));

        assertTrue(error.getMessage().startsWith("referencia"));
        verifyNoInteractions(adeudo);
    }

    @Test
    void siElAdeudoNoCoincideNoSeResuelveElDestino() {
        Producto producto = producto(2, 5, null);
        PagoServicioRequest request = request("100.00", null, "REF999");
        doThrow(new ValidacionException("monto: debe ser igual al adeudo de la referencia (250.50)"))
                .when(adeudo).validar(producto, request);

        assertThrows(ValidacionException.class, () -> resolutor.resolver(producto, request));
    }

    @Test
    void siLaVerificacionDelAdeudoFallaSePropagaElError() {
        Producto producto = producto(2, 5, null);
        PagoServicioRequest request = request("100.00", null, "REF999");
        doThrow(new NegocioException(HttpStatus.UNPROCESSABLE_ENTITY, "No fue posible verificar la referencia"))
                .when(adeudo).validar(producto, request);

        assertThrows(NegocioException.class, () -> resolutor.resolver(producto, request));
    }

    @Test
    void productoDeConsultaOSinTipoFrontNoSePuedePagar() {
        for (Integer tipoFront : new Integer[] {4, 3, null}) {
            Producto producto = producto(1, 5, null);
            producto.setTipoFront(tipoFront);

            ValidacionException error = assertThrows(ValidacionException.class,
                    () -> resolutor.resolver(producto, request("100.00", "5512345678", "REF999")));

            assertEquals("idProducto: este producto no se puede pagar", error.getMessage());
        }
        verifyNoInteractions(adeudo);
    }

    private Producto producto(int tipoFront, int idCatTipoServicio, String precio) {
        Producto producto = new Producto();
        producto.setIdProducto(20);
        producto.setIdServicio(30);
        producto.setTipoFront(tipoFront);
        producto.setIdCatTipoServicio(idCatTipoServicio);
        producto.setPrecio(precio == null ? null : new BigDecimal(precio));
        return producto;
    }

    private PagoServicioRequest request(String monto, String telefono, String referencia) {
        return new PagoServicioRequest("000000000000000001", 20, 30, new BigDecimal(monto), telefono, referencia,
                "1234");
    }
}
