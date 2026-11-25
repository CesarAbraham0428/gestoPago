package com.proyecto.servicios.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.proyecto.servicios.entity.gestopago.Producto;
import com.proyecto.servicios.entity.sf.Cuenta;
import com.proyecto.servicios.models.request.PagoServicioRequest;
import com.proyecto.servicios.service.exception.ValidacionException;
import com.proyecto.servicios.utils.FirmaGestoPago;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class ResolutorConciliacionComplementoTest {

    @Test
    void unProductoSinTipoFrontNoSePuedePagar() {
        ResolutorDestinoPago resolutor = new ResolutorDestinoPago(mock(VerificadorAdeudo.class));
        Producto producto = new Producto();
        producto.setTipoFront(null);
        PagoServicioRequest request =
                new PagoServicioRequest("000000000000000001", 1, 2, BigDecimal.TEN, null, null, "123456");

        assertThrows(ValidacionException.class, () -> resolutor.resolver(producto, request));
        producto.setTipoFront(9);
        assertThrows(ValidacionException.class, () -> resolutor.resolver(producto, request));
    }

    @Test
    void elConstructorPublicoDeLaConciliacionUsaElRelojDelSistema() {
        PagoRegistroService registro = mock(PagoRegistroService.class);
        when(registro.idsPorConfirmar(any(), any(), any())).thenReturn(List.of());

        new ConciliacionPagoService(registro, mock(FirmaGestoPago.class), mock(GestoPagoPagosGateway.class))
                .conciliarPendientes();

        verify(registro).idsPorConfirmar(any(), any(), any());
    }
}
