package com.proyecto.servicios.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.proyecto.servicios.entity.gestopago.Producto;
import com.proyecto.servicios.models.request.PagoServicioRequest;
import com.proyecto.servicios.models.response.gestopago.GestoPagoPagosResponse;
import com.proyecto.servicios.repositorys.gestopago.ProductoRepository;
import com.proyecto.servicios.service.exception.NegocioException;
import com.proyecto.servicios.service.exception.ValidacionException;
import com.proyecto.servicios.utils.FirmaGestoPago;
import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class VerificadorAdeudoTest {

    private static final String LLAVE = "HPo7OLqB4Fkk4E2yGOtwqw8H5fHR9kNx67OR4g4UdlA=";
    private static final String IV = "p5ldmBPdd/9pjC0bDC/nSg==";
    private final ObjectMapper mapper = new ObjectMapper();
    private final ProductoRepository productos = mock(ProductoRepository.class);
    private final FirmaGestoPago firma = new FirmaGestoPago(mapper, LLAVE, IV);
    private final GestoPagoPagosGateway gateway = mock(GestoPagoPagosGateway.class);
    private final VerificadorAdeudo verificador = new VerificadorAdeudo(productos, firma, gateway);
    private final Producto producto = producto(20, 30, 2);

    @Test
    void sinProductoDeConsultaNoSeLlamaAGestoPago() {
        when(productos.findFirstByIdServicioAndTipoFront(30, 4)).thenReturn(Optional.empty());

        assertDoesNotThrow(() -> verificador.validar(producto, request("250.00")));

        verifyNoInteractions(gateway);
    }

    @Test
    void consultaConElProductoTipoFront4DelServicioYAceptaElMontoIgualAlAdeudo() throws Exception {
        consulta();
        when(gateway.verificarReferencia(anyString())).thenReturn(adeudo("100.00"));

        assertDoesNotThrow(() -> verificador.validar(producto, request("100.00")));

        ArgumentCaptor<String> firmada = ArgumentCaptor.forClass(String.class);
        verify(gateway).verificarReferencia(firmada.capture());
        assertEquals(Map.of("idProducto", 40, "idServicio", 30, "referencia", "REF999"),
                mapper.readValue(firma.descifrar(firmada.getValue()), Map.class));
    }

    @Test
    void montoDistintoAlAdeudoSeRechaza() {
        consulta();
        when(gateway.verificarReferencia(anyString())).thenReturn(adeudo("250.50"));

        ValidacionException error = assertThrows(ValidacionException.class,
                () -> verificador.validar(producto, request("100.00")));

        assertEquals("monto: debe ser igual al adeudo de la referencia (250.50)", error.getMessage());
    }

    @Test
    void montoConDistintaEscalaPeroMismoValorSeAcepta() {
        consulta();
        when(gateway.verificarReferencia(anyString())).thenReturn(adeudo("100.0"));

        assertDoesNotThrow(() -> verificador.validar(producto, request("100.00")));
    }

    @Test
    void referenciaSinAdeudoOConSaldoAFavorSeRechaza() {
        consulta();
        for (String saldo : new String[] {"0", "-150.00"}) {
            when(gateway.verificarReferencia(anyString())).thenReturn(adeudo(saldo));

            NegocioException error = assertThrows(NegocioException.class,
                    () -> verificador.validar(producto, request("100.00")));

            assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, error.getStatus());
            assertEquals("La referencia no tiene adeudo pendiente", error.getMessage());
        }
    }

    @Test
    void consultaConCodigoDistintoDe01SeRechaza() {
        consulta();
        GestoPagoPagosResponse fallida = adeudo("100.00");
        fallida.setCodigo("15");
        when(gateway.verificarReferencia(anyString())).thenReturn(fallida);

        NegocioException error = assertThrows(NegocioException.class,
                () -> verificador.validar(producto, request("100.00")));

        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, error.getStatus());
        assertEquals("No fue posible verificar la referencia; no se realizó ningún cargo", error.getMessage());
    }

    @Test
    void consultaSinSaldoSeRechaza() {
        consulta();
        GestoPagoPagosResponse sinSaldo = new GestoPagoPagosResponse();
        sinSaldo.setCodigo("01");
        when(gateway.verificarReferencia(anyString())).thenReturn(sinSaldo);

        assertThrows(NegocioException.class, () -> verificador.validar(producto, request("100.00")));
    }

    private void consulta() {
        when(productos.findFirstByIdServicioAndTipoFront(30, 4)).thenReturn(Optional.of(producto(40, 30, 4)));
    }

    private Producto producto(int idProducto, int idServicio, int tipoFront) {
        Producto nuevo = new Producto();
        nuevo.setIdProducto(idProducto);
        nuevo.setIdServicio(idServicio);
        nuevo.setTipoFront(tipoFront);
        return nuevo;
    }

    private GestoPagoPagosResponse adeudo(String saldo) {
        GestoPagoPagosResponse respuesta = new GestoPagoPagosResponse();
        respuesta.setCodigo("01");
        respuesta.setSaldo(new BigDecimal(saldo));
        return respuesta;
    }

    private PagoServicioRequest request(String monto) {
        return new PagoServicioRequest("000000000000000001", 20, 30, new BigDecimal(monto), null, "REF999", "1234");
    }
}
