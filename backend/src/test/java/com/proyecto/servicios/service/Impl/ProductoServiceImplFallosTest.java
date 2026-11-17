package com.proyecto.servicios.service.Impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.proyecto.servicios.cache.CatalogoProductosCache;
import com.proyecto.servicios.cache.CatalogoProductosCache.ResultadoLectura;
import com.proyecto.servicios.client.GestoPagoCatalogException;
import com.proyecto.servicios.models.response.CatalogoProductosResponse;
import com.proyecto.servicios.models.response.ProductoResponse;
import com.proyecto.servicios.service.CatalogoProductosPersistenceService;
import com.proyecto.servicios.service.exception.CatalogoPersistenciaException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;

class ProductoServiceImplFallosTest {

    private final ConsultorCatalogoGestoPago gestoPago = mock(ConsultorCatalogoGestoPago.class);
    private final CatalogoProductosCache cache = mock(CatalogoProductosCache.class);
    private final CatalogoProductosPersistenceService persistencia = mock(CatalogoProductosPersistenceService.class);
    private final ProductoServiceImpl servicio = new ProductoServiceImpl(gestoPago, cache, persistencia);

    private static List<ProductoResponse> productos() {
        ProductoResponse producto = new ProductoResponse();
        producto.setProducto("Telcel");
        producto.setServicio("Recargas");
        producto.setIdServicio(1);
        producto.setIdProducto(2);
        return List.of(producto);
    }

    @Test
    void siPostgresFallaAlLeerSeContinuaConGestoPagoYSeGuardaElResultado() {
        when(cache.obtener()).thenReturn(new ResultadoLectura(null, true));
        when(persistencia.obtenerCatalogo()).thenThrow(new DataAccessResourceFailureException("bd"));
        CatalogoProductosResponse delProveedor = CatalogoProductosResponse.de(0, "ok", productos());
        when(gestoPago.consultar()).thenReturn(delProveedor);
        CatalogoProductosResponse persistido = CatalogoProductosResponse.de(0, "bd", productos());
        when(persistencia.persistirCatalogoRecuperado(delProveedor.getProductos())).thenReturn(persistido);
        when(cache.guardar(persistido)).thenReturn(true);

        assertEquals(persistido, servicio.obtenerProductos());
    }

    @Test
    void siPostgresLanzaUnaExcepcionDePersistenciaTambienSeContinuaConGestoPago() {
        when(cache.obtener()).thenReturn(new ResultadoLectura(null, true));
        when(persistencia.obtenerCatalogo()).thenThrow(new CatalogoPersistenciaException("x"));
        when(gestoPago.consultar()).thenReturn(CatalogoProductosResponse.de(1, "falló", List.of()));

        assertThrows(GestoPagoCatalogException.class, servicio::obtenerProductos);
    }

    @Test
    void unCodigoDeBaseDeDatosDelConsultorSeConvierteEnExcepcionDePersistencia() {
        when(cache.obtener()).thenReturn(new ResultadoLectura(null, true));
        when(gestoPago.consultar()).thenReturn(CatalogoProductosResponse.de(2, "bd", List.of()));

        assertThrows(CatalogoPersistenciaException.class, servicio::obtenerProductos);
    }

    @Test
    void unFalloDeBaseDeDatosAlSincronizarNoSePropaga() {
        when(gestoPago.consultar()).thenReturn(CatalogoProductosResponse.de(0, "ok", productos()));
        when(persistencia.actualizarSiHayMasProductos(any())).thenThrow(new CatalogoPersistenciaException("x"));

        servicio.sincronizarCatalogo();

        verify(cache, never()).guardar(any());
    }

    @Test
    void sincronizarSeOmiteSiElProveedorNoDevuelveUnCatalogoValido() {
        when(gestoPago.consultar()).thenReturn(CatalogoProductosResponse.de(1, "falló", List.of()));

        servicio.sincronizarCatalogo();

        verify(persistencia, never()).actualizarSiHayMasProductos(any());
    }
}
