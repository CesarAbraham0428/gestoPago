package com.proyecto.servicios.service.Impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.proyecto.servicios.entity.gestopago.Producto;
import com.proyecto.servicios.mapper.ProductoMapper;
import com.proyecto.servicios.models.response.CatalogoProductosResponse;
import com.proyecto.servicios.models.response.ProductoResponse;
import com.proyecto.servicios.repositorys.gestopago.ProductoRepository;
import com.proyecto.servicios.service.CatalogoProductosPersistenceService.ResultadoActualizacion;
import com.proyecto.servicios.service.exception.CatalogoPersistenciaException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.springframework.dao.DataAccessResourceFailureException;

class CatalogoProductosPersistenceCasosTest {

    private final ProductoRepository repositorio = mock(ProductoRepository.class);
    private final CatalogoProductosPersistenceServiceImpl persistencia =
            new CatalogoProductosPersistenceServiceImpl(repositorio, Mappers.getMapper(ProductoMapper.class));

    private static ProductoResponse respuesta(String nombre) {
        ProductoResponse producto = new ProductoResponse();
        producto.setProducto(nombre);
        producto.setServicio("Servicio");
        producto.setIdServicio(1);
        producto.setIdProducto(2);
        return producto;
    }

    private static Producto entidad(String nombre) {
        Producto producto = new Producto();
        producto.setProducto(nombre);
        producto.setServicio("Servicio");
        producto.setIdServicio(1);
        producto.setIdProducto(2);
        return producto;
    }

    @Test
    void unaTablaVaciaNoTieneCatalogo() {
        when(repositorio.findAllByOrderByIdAsc()).thenReturn(List.of());

        assertNull(persistencia.obtenerCatalogo());
    }

    @Test
    void unCatalogoValidoSeLeeConCodigoCero() {
        when(repositorio.findAllByOrderByIdAsc()).thenReturn(List.of(entidad("Telcel")));

        CatalogoProductosResponse catalogo = persistencia.obtenerCatalogo();

        assertEquals(0, catalogo.getCodigo());
        assertEquals(1, catalogo.getTotal());
    }

    @Test
    void unFalloDeLaBaseSePropagaAlLlamador() {
        when(repositorio.findAllByOrderByIdAsc()).thenThrow(new DataAccessResourceFailureException("bd"));

        assertThrows(DataAccessResourceFailureException.class, persistencia::obtenerCatalogo);
    }

    @Test
    void unCatalogoNuevoInvalidoNoModificaLaBase() {
        when(repositorio.count()).thenReturn(5L);

        ResultadoActualizacion resultado = persistencia.actualizarSiHayMasProductos(null);

        assertFalse(resultado.actualizado());
        assertEquals(5, resultado.cantidadAnterior());
        verify(repositorio, never()).deleteAllInBatch();
    }

    @Test
    void siElCatalogoActualEsInvalidoSeConservaPeroNoSeOfreceParaLaCache() {
        when(repositorio.count()).thenReturn(3L);
        when(repositorio.findAllByOrderByIdAsc()).thenReturn(List.of(new Producto()));

        ResultadoActualizacion resultado = persistencia.actualizarSiHayMasProductos(List.of(respuesta("A")));

        assertFalse(resultado.actualizado());
        assertNull(resultado.catalogoPersistido());
    }

    @Test
    void persistirRechazaUnCatalogoInvalido() {
        assertThrows(CatalogoPersistenciaException.class, () -> persistencia.persistirCatalogoRecuperado(null));
        assertThrows(CatalogoPersistenciaException.class,
                () -> persistencia.persistirCatalogoRecuperado(List.of(new ProductoResponse())));
        verify(repositorio, never()).deleteAllInBatch();
    }

    @Test
    void persistirReemplazaElCatalogoYDevuelveLoGuardado() {
        when(repositorio.count()).thenReturn(1L);
        when(repositorio.findAllByOrderByIdAsc()).thenReturn(List.of(entidad("Telcel")));

        CatalogoProductosResponse persistido = persistencia.persistirCatalogoRecuperado(List.of(respuesta("Telcel")));

        assertEquals(1, persistido.getTotal());
        verify(repositorio).deleteAllInBatch();
        verify(repositorio).saveAllAndFlush(anyList());
    }

    @Test
    void siLaBaseNoConservaUnCatalogoValidoTrasEscribirSeRevierte() {
        when(repositorio.findAllByOrderByIdAsc()).thenReturn(List.of(new Producto()));

        assertThrows(CatalogoPersistenciaException.class,
                () -> persistencia.persistirCatalogoRecuperado(List.of(respuesta("Telcel"))));
    }
}
