package com.proyecto.servicios.service.Impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.proyecto.servicios.entity.gestopago.Producto;
import com.proyecto.servicios.entity.sf.EstadoPago;
import com.proyecto.servicios.entity.sf.Pago;
import com.proyecto.servicios.models.response.HistorialPagoResponse;
import com.proyecto.servicios.models.response.ListaGenericResponse;
import com.proyecto.servicios.repositorys.gestopago.ProductoRepository;
import com.proyecto.servicios.service.GestoPagoPagosGateway;
import com.proyecto.servicios.service.PagoRegistroService;
import com.proyecto.servicios.service.ResolutorDestinoPago;
import com.proyecto.servicios.utils.FirmaGestoPago;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

class PagoServiceImplHistorialTest {

    private final ProductoRepository productos = mock(ProductoRepository.class);
    private final PagoRegistroService registro = mock(PagoRegistroService.class);
    private final PagoServiceImpl service = new PagoServiceImpl(productos, registro,
            mock(GestoPagoPagosGateway.class), mock(ResolutorDestinoPago.class), mock(FirmaGestoPago.class),
            "Sucursal Centro", "America/Mexico_City");

    private static Pago pago(int idProducto, int idServicio, String upc) {
        Pago p = new Pago();
        p.setUpc(upc);
        p.setEstado(EstadoPago.APROBADO);
        p.setIdProducto(idProducto);
        p.setIdServicio(idServicio);
        p.setMonto(new BigDecimal("100.00"));
        p.setComisionCliente(new BigDecimal("10.00"));
        p.setReferencia("REF");
        p.setTelefono("1111111111");
        p.setIdTx(9L);
        p.setNumAutorizacion("AUT");
        return p;
    }

    @Test
    void elHistorialAgregaProductoYServicioDelCatalogoConsultandoCadaParUnaVez() {
        Producto telcel = new Producto();
        telcel.setProducto("Telcel");
        telcel.setServicio("Recargas");
        when(productos.findFirstByIdProductoAndIdServicio(1, 2)).thenReturn(Optional.of(telcel));
        when(productos.findFirstByIdProductoAndIdServicio(3, 4)).thenReturn(Optional.empty());
        when(registro.aprobadosDeUsuario(7, 0, 10)).thenReturn(new PageImpl<>(
                List.of(pago(1, 2, "A"), pago(1, 2, "B"), pago(3, 4, "C")), PageRequest.of(0, 10), 3));

        ListaGenericResponse<HistorialPagoResponse> historial = service.historial(7, 0, 10);

        assertEquals(3, historial.totalElementos());
        HistorialPagoResponse primero = historial.datos().get(0);
        assertEquals("Telcel", primero.producto());
        assertEquals("Recargas", primero.servicio());
        assertEquals(new BigDecimal("110.00"), primero.total());
        assertNull(historial.datos().get(2).producto());
        verify(productos, times(1)).findFirstByIdProductoAndIdServicio(1, 2);
        verify(productos, times(1)).findFirstByIdProductoAndIdServicio(3, 4);
        verify(productos, times(2)).findFirstByIdProductoAndIdServicio(anyInt(), any());
    }
}
