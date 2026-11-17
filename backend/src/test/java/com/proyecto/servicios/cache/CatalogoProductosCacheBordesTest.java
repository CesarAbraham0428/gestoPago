package com.proyecto.servicios.cache;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.proyecto.servicios.cache.CatalogoProductosCache.ResultadoLectura;
import com.proyecto.servicios.models.response.CatalogoProductosResponse;
import com.proyecto.servicios.models.response.ProductoResponse;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

class CatalogoProductosCacheBordesTest {

    private static final String CLAVE = "gestopago:catalogo:productos:v1";
    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> valores = mock(ValueOperations.class);
    private final ObjectMapper mapper = new ObjectMapper();
    private final CatalogoProductosCache cache = new CatalogoProductosCache(redis, mapper, Duration.ofHours(1));

    CatalogoProductosCacheBordesTest() {
        when(redis.opsForValue()).thenReturn(valores);
    }

    private static CatalogoProductosResponse catalogo() {
        ProductoResponse producto = new ProductoResponse();
        producto.setProducto("Telcel");
        producto.setServicio("Recargas");
        producto.setIdServicio(1);
        producto.setIdProducto(2);
        return CatalogoProductosResponse.de(0, "ok", List.of(producto));
    }

    @Test
    void unValorVacioSeEliminaYSeReportaSegunLaConfirmacionDeRedis() {
        when(valores.get(CLAVE)).thenReturn("  ");
        when(redis.delete(CLAVE)).thenReturn(true, false, null);

        ResultadoLectura eliminado = cache.obtener();
        ResultadoLectura sinConfirmar = cache.obtener();
        ResultadoLectura nulo = cache.obtener();

        assertNull(eliminado.catalogo());
        assertTrue(eliminado.redisDisponible());
        assertFalse(sinConfirmar.redisDisponible());
        assertFalse(nulo.redisDisponible());
    }

    @Test
    void siRedisFallaAlEliminarUnValorInvalidoNoSeConsideraDisponible() {
        when(valores.get(CLAVE)).thenReturn("no-es-json");
        when(redis.delete(CLAVE)).thenThrow(new RedisConnectionFailureException("caído"));

        ResultadoLectura lectura = cache.obtener();

        assertNull(lectura.catalogo());
        assertFalse(lectura.redisDisponible());
    }

    @Test
    void unCatalogoGuardadoPuedeLeerseDeNuevo() throws Exception {
        String json = mapper.writeValueAsString(catalogo());
        when(valores.get(CLAVE)).thenReturn(json);

        ResultadoLectura lectura = cache.obtener();

        assertEquals(1, lectura.catalogo().getProductos().size());
        assertTrue(lectura.redisDisponible());
    }

    @Test
    void guardarEscribeElJsonConElTtlConfigurado() {
        assertTrue(cache.guardar(catalogo()));

        verify(valores).set(anyString(), anyString(), org.mockito.ArgumentMatchers.eq(Duration.ofHours(1)));
    }

    @Test
    void guardarRechazaUnCatalogoNuloOSinProductos() {
        assertFalse(cache.guardar(null));
        CatalogoProductosResponse sinProductos = new CatalogoProductosResponse();
        assertFalse(cache.guardar(sinProductos));
        assertFalse(cache.guardar(CatalogoProductosResponse.de(0, "vacío", List.of())));
    }

    @Test
    void siNoSePuedeSerializarSeInvalidaElCatalogoAnterior() throws Exception {
        ObjectMapper roto = mock(ObjectMapper.class);
        when(roto.writeValueAsString(any())).thenThrow(new JsonProcessingException("x") { });
        CatalogoProductosCache conMapperRoto = new CatalogoProductosCache(redis, roto, Duration.ofHours(1));
        when(redis.delete(CLAVE)).thenReturn(true);

        assertFalse(conMapperRoto.guardar(catalogo()));

        verify(redis).delete(CLAVE);
    }

    @Test
    void siRedisFallaAlEscribirSeIntentaInvalidarYSeDevuelveFalso() {
        org.mockito.Mockito.doThrow(new RedisConnectionFailureException("caído")).when(valores)
                .set(anyString(), anyString(), any(Duration.class));
        when(redis.delete(CLAVE)).thenReturn(false);

        assertFalse(cache.guardar(catalogo()));

        verify(redis).delete(CLAVE);
    }
}
