package com.proyecto.servicios.service.Impl;

import com.proyecto.servicios.cache.CatalogoProductosCache;
import com.proyecto.servicios.client.GestoPagoCatalogClient;
import com.proyecto.servicios.client.GestoPagoCatalogException;
import com.proyecto.servicios.entity.gestopago.Producto;
import com.proyecto.servicios.entity.gestopago.GestoPagoToken;
import com.proyecto.servicios.model.CatalogoProductosResponse;
import com.proyecto.servicios.model.ProductoResponse;
import com.proyecto.servicios.model.gestopago.GestoPagoCatalogMessage;
import com.proyecto.servicios.model.gestopago.GestoPagoCatalogResponse;
import com.proyecto.servicios.model.gestopago.GestoPagoProduct;
import com.proyecto.servicios.service.CatalogoProductosPersistenceService;
import com.proyecto.servicios.service.CatalogoProductosPersistenceService.ResultadoActualizacion;
import com.proyecto.servicios.service.GestoPagoTokenService;
import com.proyecto.servicios.repositorys.gestopago.ProductoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductoServiceImplTest {

    @Mock
    private GestoPagoCatalogClient catalogClient;
    @Mock
    private CatalogoProductosCache cache;
    @Mock
    private CatalogoProductosPersistenceService persistence;
    @Mock
    private GestoPagoTokenService tokenService;
    @Mock
    private ProductoRepository productoRepository;

    private ProductoServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ProductoServiceImpl(catalogClient, cache, persistence, tokenService, 42, "device-test");
    }

    @Test
    void obtenerProductos_respondeDesdeRedisSinConsultarOtrasFuentes() {
        CatalogoProductosResponse redisCatalog = catalog("Redis", 0);
        when(cache.obtener()).thenReturn(new CatalogoProductosCache.ResultadoLectura(Optional.of(redisCatalog), true));

        CatalogoProductosResponse result = service.obtenerProductos();

        assertSame(redisCatalog, result);
        assertEquals(0, result.getCodigo());
        assertEquals("Catálogo obtenido correctamente desde Redis", result.getMensaje());
        verifyNoInteractions(persistence, catalogClient, tokenService);
        verify(cache, never()).guardar(any());
    }

    @Test
    void obtenerProductos_siCacheInvalidaFueDescartadaContinuaConPostgresYReconstruyeRedis() {
        CatalogoProductosResponse pgCatalog = catalog("Postgres", 0);
        when(cache.obtener()).thenReturn(new CatalogoProductosCache.ResultadoLectura(Optional.empty(), true));
        when(persistence.obtenerCatalogo()).thenReturn(Optional.of(pgCatalog));
        when(cache.guardar(pgCatalog)).thenReturn(true);

        CatalogoProductosResponse result = service.obtenerProductos();

        assertSame(pgCatalog, result);
        assertEquals(0, result.getCodigo());
        verify(cache).guardar(pgCatalog);
        verifyNoInteractions(catalogClient, tokenService);
    }

    @Test
    void obtenerProductos_siRedisNoDisponibleRespondeDesdePostgresConCodigoTres() {
        CatalogoProductosResponse pgCatalog = catalog("Postgres", 0);
        when(cache.obtener()).thenReturn(new CatalogoProductosCache.ResultadoLectura(Optional.empty(), false));
        when(persistence.obtenerCatalogo()).thenReturn(Optional.of(pgCatalog));
        when(cache.guardar(pgCatalog)).thenReturn(false);

        CatalogoProductosResponse result = service.obtenerProductos();

        assertEquals(3, result.getCodigo());
        assertEquals(1, result.getTotal());
        assertEquals("Producto de Postgres", result.getProductos().get(0).getProducto());
        verifyNoInteractions(catalogClient, tokenService);
    }

    @Test
    void obtenerProductos_guardaRespuestaDeGestoPagoEnPostgresAntesQueEnRedis() {
        CatalogoProductosResponse storedCatalog = catalog("Postgres", 0);
        mockEmptySourcesAndProvider(providerCatalog(validProviderProduct("Proveedor")));
        when(persistence.persistirCatalogoRecuperado(any())).thenReturn(storedCatalog);
        when(cache.guardar(storedCatalog)).thenReturn(true);

        CatalogoProductosResponse result = service.obtenerProductos();

        assertSame(storedCatalog, result);
        assertEquals(0, result.getCodigo());
        InOrder order = inOrder(persistence, cache);
        order.verify(persistence).persistirCatalogoRecuperado(any());
        order.verify(cache).guardar(storedCatalog);
    }

    @Test
    void obtenerProductos_siPostgresTieneUnCatalogoIncompletoLoSaltaYConsultaGestoPago() {
        Producto invalidStoredProduct = new Producto();
        invalidStoredProduct.setProducto("Producto incompleto");
        invalidStoredProduct.setServicio(null);
        invalidStoredProduct.setIdServicio(1);
        invalidStoredProduct.setIdProducto(1);
        Producto validPersistedProduct = new Producto();
        validPersistedProduct.setProducto("Producto externo");
        validPersistedProduct.setServicio("Servicio externo");
        validPersistedProduct.setIdServicio(10);
        validPersistedProduct.setIdProducto(20);

        when(cache.obtener()).thenReturn(new CatalogoProductosCache.ResultadoLectura(Optional.empty(), true));
        when(productoRepository.findAllByOrderByIdAsc())
                .thenReturn(List.of(invalidStoredProduct), List.of(validPersistedProduct));
        when(productoRepository.count()).thenReturn(1L);
        when(cache.guardar(any())).thenReturn(true);
        mockProviderAndToken(providerCatalog(validProviderProduct("Producto externo")));
        service = new ProductoServiceImpl(
                catalogClient,
                cache,
                new CatalogoProductosPersistenceService(productoRepository),
                tokenService,
                42,
                "device-test");

        CatalogoProductosResponse result = service.obtenerProductos();

        assertEquals(0, result.getCodigo());
        assertEquals("Producto externo", result.getProductos().get(0).getProducto());
        verify(catalogClient).consultarCatalogo("token-test");
        verify(productoRepository).deleteAllInBatch();
        verify(cache).guardar(any());
    }

    @Test
    void obtenerProductos_respuestaVaciaDeGestoPagoNoEscribeEnPostgresNiRedis() {
        mockEmptySourcesAndProvider(providerCatalog());

        CatalogoProductosResponse result = service.obtenerProductos();

        assertEquals(1, result.getCodigo());
        assertEquals(0, result.getTotal());
        verify(persistence, never()).persistirCatalogoRecuperado(any());
        verify(persistence, never()).actualizarSiHayMasProductos(any());
        verify(cache, never()).guardar(any());
    }

    @Test
    void obtenerProductos_productoIncompletoDeGestoPagoNoEscribeEnPostgresNiRedis() {
        GestoPagoProduct invalid = validProviderProduct(" ");
        mockEmptySourcesAndProvider(providerCatalog(invalid));

        CatalogoProductosResponse result = service.obtenerProductos();

        assertEquals(1, result.getCodigo());
        assertEquals(0, result.getTotal());
        verify(persistence, never()).persistirCatalogoRecuperado(any());
        verify(persistence, never()).actualizarSiHayMasProductos(any());
        verify(cache, never()).guardar(any());
    }

    @Test
    void obtenerProductos_errorDelClienteExternoSeTraduceACodigoUnoSinEscrituras() {
        mockEmptySourcesAndToken();
        when(catalogClient.consultarCatalogo("token-test"))
                .thenThrow(new GestoPagoCatalogException("Fallo externo", new RuntimeException("timeout")));

        CatalogoProductosResponse result = service.obtenerProductos();

        assertEquals(1, result.getCodigo());
        assertEquals(0, result.getTotal());
        verify(persistence, never()).persistirCatalogoRecuperado(any());
        verify(persistence, never()).actualizarSiHayMasProductos(any());
        verify(cache, never()).guardar(any());
    }

    @Test
    void obtenerProductos_siPostgresFallaAlGuardarNoEscribeEnRedis() {
        mockEmptySourcesAndProvider(providerCatalog(validProviderProduct("Proveedor")));
        when(persistence.persistirCatalogoRecuperado(any()))
                .thenThrow(new DataAccessResourceFailureException("database unavailable"));

        CatalogoProductosResponse result = service.obtenerProductos();

        assertEquals(2, result.getCodigo());
        assertEquals(0, result.getTotal());
        verify(cache, never()).guardar(any());
    }

    @Test
    void obtenerProductos_siRedisFallaDespuesDePersistirDevuelveLosDatosPersistidos() {
        CatalogoProductosResponse storedCatalog = catalog("Persistido", 0);
        mockEmptySourcesAndProvider(providerCatalog(validProviderProduct("Proveedor")));
        when(persistence.persistirCatalogoRecuperado(any())).thenReturn(storedCatalog);
        when(cache.guardar(storedCatalog)).thenReturn(false);

        CatalogoProductosResponse result = service.obtenerProductos();

        assertEquals(3, result.getCodigo());
        assertEquals(1, result.getTotal());
        assertEquals("Producto de Persistido", result.getProductos().get(0).getProducto());
        verify(persistence).persistirCatalogoRecuperado(any());
        verify(cache).guardar(storedCatalog);
    }

    @Test
    void obtenerProductos_401RenuevaTokenUnaVezYReintentaConElTokenNuevo() {
        GestoPagoToken refreshedToken = new GestoPagoToken();
        refreshedToken.setToken("renewed-token");
        CatalogoProductosResponse storedCatalog = catalog("Persistido", 0);
        mockEmptySourcesAndToken();
        when(catalogClient.consultarCatalogo("token-test")).thenThrow(authRejected());
        when(tokenService.renovarToken()).thenReturn(refreshedToken);
        when(catalogClient.consultarCatalogo("renewed-token"))
                .thenReturn(providerCatalog(validProviderProduct("Proveedor")));
        when(persistence.persistirCatalogoRecuperado(any())).thenReturn(storedCatalog);
        when(cache.guardar(storedCatalog)).thenReturn(true);

        CatalogoProductosResponse result = service.obtenerProductos();

        assertEquals(0, result.getCodigo());
        verify(catalogClient, times(1)).consultarCatalogo("token-test");
        verify(tokenService, times(1)).renovarToken();
        verify(catalogClient, times(1)).consultarCatalogo("renewed-token");
        InOrder order = inOrder(catalogClient, tokenService, persistence, cache);
        order.verify(catalogClient).consultarCatalogo("token-test");
        order.verify(tokenService).renovarToken();
        order.verify(catalogClient).consultarCatalogo("renewed-token");
        order.verify(persistence).persistirCatalogoRecuperado(any());
        order.verify(cache).guardar(storedCatalog);
    }

    @Test
    void obtenerProductos_siElReintentoTambienDevuelve401SeDetieneSinPersistir() {
        GestoPagoToken refreshedToken = new GestoPagoToken();
        refreshedToken.setToken("renewed-token");
        mockEmptySourcesAndToken();
        when(catalogClient.consultarCatalogo("token-test")).thenThrow(authRejected());
        when(tokenService.renovarToken()).thenReturn(refreshedToken);
        when(catalogClient.consultarCatalogo("renewed-token")).thenThrow(authRejected());

        CatalogoProductosResponse result = service.obtenerProductos();

        assertEquals(1, result.getCodigo());
        assertEquals(0, result.getTotal());
        verify(catalogClient, times(1)).consultarCatalogo("token-test");
        verify(tokenService, times(1)).renovarToken();
        verify(catalogClient, times(1)).consultarCatalogo("renewed-token");
        verify(persistence, never()).persistirCatalogoRecuperado(any());
        verify(persistence, never()).actualizarSiHayMasProductos(any());
        verify(cache, never()).guardar(any());
    }

    @Test
    void sincronizarCatalogo_conCantidadNuevaMayorActualizaPostgresYDespuesRedis() {
        GestoPagoCatalogResponse providerCatalog = providerCatalog(validProviderProduct("Nuevo"), validProviderProduct("Otro"));
        mockProviderAndToken(providerCatalog);
        CatalogoProductosResponse storedCatalog = catalog("Actualizado", 0);
        when(persistence.actualizarSiHayMasProductos(any())).thenReturn(new ResultadoActualizacion(true, 1, storedCatalog));
        when(cache.guardar(storedCatalog)).thenReturn(true);

        service.sincronizarCatalogo();

        InOrder order = inOrder(persistence, cache);
        order.verify(persistence).actualizarSiHayMasProductos(any());
        order.verify(cache).guardar(storedCatalog);
    }

    @Test
    void sincronizarCatalogo_conCantidadNuevaMenorConservaCatalogoYNoActualizaRedis() {
        mockProviderAndToken(providerCatalog(validProviderProduct("Menor")));
        when(persistence.actualizarSiHayMasProductos(any()))
                .thenReturn(new ResultadoActualizacion(false, 3, catalog("Existente", 0)));

        service.sincronizarCatalogo();

        verify(persistence).actualizarSiHayMasProductos(any());
        verify(cache, never()).guardar(any());
    }

    @Test
    void sincronizarCatalogo_conCantidadNuevaIgualConservaCatalogoYNoActualizaRedis() {
        mockProviderAndToken(providerCatalog(validProviderProduct("Igual"), validProviderProduct("Igual-2")));
        when(persistence.actualizarSiHayMasProductos(any()))
                .thenReturn(new ResultadoActualizacion(false, 2, catalog("Existente", 0)));

        service.sincronizarCatalogo();

        verify(persistence).actualizarSiHayMasProductos(any());
        verify(cache, never()).guardar(any());
    }

    @Test
    void sincronizarCatalogo_sinCatalogoPrevioRealizaCargaInicialEnPostgresYRedis() {
        mockProviderAndToken(providerCatalog(validProviderProduct("Inicial")));
        CatalogoProductosResponse storedCatalog = catalog("Inicial", 0);
        when(persistence.actualizarSiHayMasProductos(any())).thenReturn(new ResultadoActualizacion(true, 0, storedCatalog));
        when(cache.guardar(storedCatalog)).thenReturn(true);

        service.sincronizarCatalogo();

        InOrder order = inOrder(persistence, cache);
        order.verify(persistence).actualizarSiHayMasProductos(any());
        order.verify(cache).guardar(storedCatalog);
    }

    @Test
    void sincronizarCatalogo_siProveedorDevuelveVacioNoModificaAlmacenamientos() {
        mockProviderAndToken(providerCatalog());

        service.sincronizarCatalogo();

        verify(persistence, never()).actualizarSiHayMasProductos(any());
        verify(cache, never()).guardar(any());
    }

    private void mockEmptySourcesAndProvider(GestoPagoCatalogResponse providerResponse) {
        mockEmptySourcesAndToken();
        when(catalogClient.consultarCatalogo("token-test")).thenReturn(providerResponse);
    }

    private void mockProviderAndToken(GestoPagoCatalogResponse providerResponse) {
        mockToken();
        when(catalogClient.consultarCatalogo("token-test")).thenReturn(providerResponse);
    }

    private void mockToken() {
        GestoPagoToken token = new GestoPagoToken();
        token.setToken("token-test");
        when(tokenService.obtenerTokenActivo(42, "device-test")).thenReturn(Optional.of(token));
    }

    private void mockEmptySourcesAndToken() {
        when(cache.obtener()).thenReturn(new CatalogoProductosCache.ResultadoLectura(Optional.empty(), true));
        when(persistence.obtenerCatalogo()).thenReturn(Optional.empty());
        mockToken();
    }

    private static GestoPagoCatalogException authRejected() {
        return new GestoPagoCatalogException(
                "GestoPago rechazó la autenticación del catálogo",
                GestoPagoCatalogException.Tipo.AUTENTICACION,
                401,
                null);
    }

    private static GestoPagoCatalogResponse providerCatalog(GestoPagoProduct... products) {
        GestoPagoCatalogMessage message = new GestoPagoCatalogMessage();
        message.setCodigo("01");
        message.setTexto("OK");
        GestoPagoCatalogResponse response = new GestoPagoCatalogResponse();
        response.setMensaje(message);
        response.setProductos(List.of(products));
        return response;
    }

    private static GestoPagoProduct validProviderProduct(String name) {
        GestoPagoProduct product = new GestoPagoProduct();
        product.setProducto(name);
        product.setServicio("Servicio");
        product.setIdServicio(10);
        product.setIdProducto(20);
        return product;
    }

    private static CatalogoProductosResponse catalog(String source, int code) {
        ProductoResponse product = new ProductoResponse();
        product.setProducto("Producto de " + source);
        product.setServicio("Servicio de " + source);
        product.setIdServicio(1);
        product.setIdProducto(2);
        CatalogoProductosResponse response = new CatalogoProductosResponse();
        response.setCodigo(code);
        response.setMensaje(source);
        response.setProductos(List.of(product));
        response.setTotal(1);
        return response;
    }
}
