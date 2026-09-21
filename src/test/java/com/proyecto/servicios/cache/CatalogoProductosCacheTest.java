package com.proyecto.servicios.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.proyecto.servicios.model.CatalogoProductosResponse;
import com.proyecto.servicios.model.ProductoResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CatalogoProductosCacheTest {

    private static final String CACHE_KEY = "gestopago:catalogo:productos:v1";

    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;

    private CatalogoProductosCache cache;

    @BeforeEach
    void setUp() {
        cache = new CatalogoProductosCache(redisTemplate, new ObjectMapper(), Duration.ofHours(24));
    }

    @Test
    void obtener_conCacheVaciaDevuelveAusenciaYRedisDisponible() {
        stubValueOperations();
        when(valueOperations.get(CACHE_KEY)).thenReturn(null);

        CatalogoProductosCache.ResultadoLectura result = cache.obtener();

        assertTrue(result.catalogo().isEmpty());
        assertTrue(result.redisDisponible());
        verify(redisTemplate, never()).delete(anyString());
    }

    @Test
    void obtener_conJsonCorruptoLoDescartaYPermiteElFallback() {
        stubValueOperations();
        when(valueOperations.get(CACHE_KEY)).thenReturn("{not-json");
        when(redisTemplate.delete(CACHE_KEY)).thenReturn(true);

        CatalogoProductosCache.ResultadoLectura result = cache.obtener();

        assertTrue(result.catalogo().isEmpty());
        assertTrue(result.redisDisponible());
        verify(redisTemplate).delete(CACHE_KEY);
    }

    @Test
    void obtener_conProductoIncompletoLoDescartaYEliminaLaEntrada() {
        stubValueOperations();
        when(valueOperations.get(CACHE_KEY)).thenReturn(
                "{\"codigo\":0,\"mensaje\":\"ok\",\"total\":1,\"productos\":["
                        + "{\"producto\":\"agua\",\"idServicio\":1,\"idProducto\":2}]} ");
        when(redisTemplate.delete(CACHE_KEY)).thenReturn(true);

        CatalogoProductosCache.ResultadoLectura result = cache.obtener();

        assertTrue(result.catalogo().isEmpty());
        assertTrue(result.redisDisponible());
        verify(redisTemplate).delete(CACHE_KEY);
    }

    @Test
    void obtener_siRedisNoEstaDisponibleDevuelveAusenciaMarcadaComoNoDisponible() {
        stubValueOperations();
        when(valueOperations.get(CACHE_KEY))
                .thenThrow(new DataAccessResourceFailureException("Redis unavailable"));

        CatalogoProductosCache.ResultadoLectura result = cache.obtener();

        assertTrue(result.catalogo().isEmpty());
        assertFalse(result.redisDisponible());
        verify(redisTemplate, never()).delete(anyString());
    }

    @Test
    void guardar_rechazaCatalogoConProductoIncompletoSinEscribirEnRedis() {
        CatalogoProductosResponse invalidCatalog = catalogWithProduct("agua", null, 1, 2);

        boolean saved = cache.guardar(invalidCatalog);

        assertFalse(saved);
        verify(valueOperations, never()).set(anyString(), anyString(), any(Duration.class));
    }

    @Test
    void guardar_rechazaCatalogoVacioSinEscribirEnRedis() {
        CatalogoProductosResponse emptyCatalog = new CatalogoProductosResponse();
        emptyCatalog.setProductos(List.of());

        boolean saved = cache.guardar(emptyCatalog);

        assertFalse(saved);
        verify(valueOperations, never()).set(anyString(), anyString(), any(Duration.class));
    }

    @Test
    void guardar_siFallaLaEscrituraInvalidaElCatalogoAnteriorParaEvitarDatosObsoletos() {
        stubValueOperations();
        doThrow(new DataAccessResourceFailureException("Redis unavailable"))
                .when(valueOperations).set(anyString(), anyString(), any(Duration.class));
        when(redisTemplate.delete(CACHE_KEY)).thenReturn(true);

        boolean saved = cache.guardar(catalogWithProduct("nuevo", "agua", 1, 2));

        assertFalse(saved);
        verify(redisTemplate).delete(CACHE_KEY);
    }

    private static CatalogoProductosResponse catalogWithProduct(
            String productName, String serviceName, Integer serviceId, Integer productId) {
        ProductoResponse product = new ProductoResponse();
        product.setProducto(productName);
        product.setServicio(serviceName);
        product.setIdServicio(serviceId);
        product.setIdProducto(productId);
        CatalogoProductosResponse response = new CatalogoProductosResponse();
        response.setCodigo(0);
        response.setMensaje("ok");
        response.setProductos(List.of(product));
        response.setTotal(1);
        return response;
    }

    private void stubValueOperations() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }
}
