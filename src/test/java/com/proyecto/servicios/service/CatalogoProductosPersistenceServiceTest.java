package com.proyecto.servicios.service;

import com.proyecto.servicios.entity.gestopago.Producto;
import com.proyecto.servicios.mapper.ProductoMapper;
import com.proyecto.servicios.model.CatalogoProductosResponse;
import com.proyecto.servicios.model.ProductoResponse;
import com.proyecto.servicios.repositorys.gestopago.ProductoRepository;
import com.proyecto.servicios.service.Impl.CatalogoProductosPersistenceServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mapstruct.factory.Mappers;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CatalogoProductosPersistenceServiceTest {

    @Mock
    private ProductoRepository repository;

    private CatalogoProductosPersistenceService persistence;

    @BeforeEach
    void setUp() {
        persistence = new CatalogoProductosPersistenceServiceImpl(repository, Mappers.getMapper(ProductoMapper.class));
    }

    @Test
    void actualizarSiHayMasProductos_actualizaCuandoLaNuevaCantidadEsMayor() {
        when(repository.count()).thenReturn(2L);
        when(repository.findAllByOrderByIdAsc()).thenReturn(List.of(
                entity("Persistido-1"), entity("Persistido-2"), entity("Persistido-3")));

        CatalogoProductosPersistenceService.ResultadoActualizacion result =
                persistence.actualizarSiHayMasProductos(List.of(product("Nuevo-1"), product("Nuevo-2"), product("Nuevo-3")));

        assertTrue(result.actualizado());
        assertEquals(2, result.cantidadAnterior());
        assertEquals(3, result.catalogoPersistido().getTotal());
        verify(repository).deleteAllInBatch();
        verify(repository).saveAllAndFlush(anyList());
        var order = inOrder(repository);
        order.verify(repository).bloquearEscriturasCatalogo();
        order.verify(repository).count();
    }

    @Test
    void actualizarSiHayMasProductos_conCantidadMenorConservaCatalogoActual() {
        when(repository.count()).thenReturn(3L);
        when(repository.findAllByOrderByIdAsc()).thenReturn(List.of(
                entity("Actual-1"), entity("Actual-2"), entity("Actual-3")));

        CatalogoProductosPersistenceService.ResultadoActualizacion result =
                persistence.actualizarSiHayMasProductos(List.of(product("Nuevo")));

        assertFalse(result.actualizado());
        assertEquals(3, result.cantidadAnterior());
        assertEquals(3, result.catalogoPersistido().getTotal());
        assertEquals("Actual-1", result.catalogoPersistido().getProductos().get(0).getProducto());
        verify(repository, never()).deleteAllInBatch();
        verify(repository, never()).saveAllAndFlush(anyList());
    }

    @Test
    void actualizarSiHayMasProductos_conCantidadIgualConservaCatalogoActual() {
        when(repository.count()).thenReturn(2L);
        when(repository.findAllByOrderByIdAsc()).thenReturn(List.of(entity("Actual-1"), entity("Actual-2")));

        CatalogoProductosPersistenceService.ResultadoActualizacion result =
                persistence.actualizarSiHayMasProductos(List.of(product("Cambiado-1"), product("Cambiado-2")));

        assertFalse(result.actualizado());
        assertEquals(2, result.cantidadAnterior());
        assertEquals("Actual-1", result.catalogoPersistido().getProductos().get(0).getProducto());
        verify(repository, never()).deleteAllInBatch();
        verify(repository, never()).saveAllAndFlush(anyList());
    }

    @Test
    void actualizarSiHayMasProductos_sinCatalogoPrevioRealizaCargaInicial() {
        when(repository.count()).thenReturn(0L);
        when(repository.findAllByOrderByIdAsc()).thenReturn(List.of(entity("Inicial")));

        CatalogoProductosPersistenceService.ResultadoActualizacion result =
                persistence.actualizarSiHayMasProductos(List.of(product("Inicial")));

        assertTrue(result.actualizado());
        assertEquals(0, result.cantidadAnterior());
        assertEquals(1, result.catalogoPersistido().getTotal());
        verify(repository).deleteAllInBatch();
        verify(repository).saveAllAndFlush(anyList());
    }

    @Test
    void actualizarSiHayMasProductos_catalogoVacioNoEscribe() {
        when(repository.count()).thenReturn(2L);

        CatalogoProductosPersistenceService.ResultadoActualizacion result =
                persistence.actualizarSiHayMasProductos(List.of());

        assertFalse(result.actualizado());
        assertEquals(2, result.cantidadAnterior());
        assertNull(result.catalogoPersistido());
        verify(repository, never()).deleteAllInBatch();
        verify(repository, never()).saveAllAndFlush(anyList());
    }

    @Test
    void obtenerCatalogo_siPostgresContieneUnProductoIncompletoLoOmiteParaPermitirFallback() {
        Producto invalid = entity("Agua");
        invalid.setServicio(null);
        when(repository.findAllByOrderByIdAsc()).thenReturn(List.of(invalid));

        var result = persistence.obtenerCatalogo();

        assertNull(result);
        verify(repository).findAllByOrderByIdAsc();
    }

    private static ProductoResponse product(String name) {
        ProductoResponse response = new ProductoResponse();
        response.setProducto(name);
        response.setServicio("Servicio");
        response.setIdServicio(1);
        response.setIdProducto(2);
        return response;
    }

    private static Producto entity(String name) {
        Producto producto = new Producto();
        producto.setProducto(name);
        producto.setServicio("Servicio");
        producto.setIdServicio(1);
        producto.setIdProducto(2);
        return producto;
    }
}
