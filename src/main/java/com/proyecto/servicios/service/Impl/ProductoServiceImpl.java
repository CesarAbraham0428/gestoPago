package com.proyecto.servicios.service.Impl;

import com.proyecto.servicios.cache.CatalogoProductosCache;
import com.proyecto.servicios.cache.CatalogoProductosCache.ResultadoLectura;
import com.proyecto.servicios.client.GestoPagoCatalogException;
import com.proyecto.servicios.models.response.CatalogoProductosResponse;
import com.proyecto.servicios.service.CatalogoProductosPersistenceService;
import com.proyecto.servicios.service.CatalogoProductosPersistenceService.ResultadoActualizacion;
import com.proyecto.servicios.service.ProductoService;
import com.proyecto.servicios.service.exception.CatalogoPersistenciaException;
import java.time.Duration;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionException;

/**
 * Resuelve el catálogo en orden: Redis, PostgreSQL y por último GestoPago (consultado por
 * {@link ConsultorCatalogoGestoPago}). PostgreSQL es el respaldo persistente obligatorio.
 */
@Service
@Slf4j
public class ProductoServiceImpl implements ProductoService {

    private static final String MENSAJE_REDIS_NO_DISPONIBLE =
            "Se ha consultado correctamente la información del catálogo de la BD";
    private static final String MENSAJE_REDIS_EXITOSO =
            "Catálogo obtenido correctamente desde Redis";
    private static final int CODIGO_OK = 0;
    private static final int CODIGO_PROVEEDOR = 1;
    private static final int CODIGO_BD = 2;

    private final ConsultorCatalogoGestoPago gestoPago;
    private final CatalogoProductosCache catalogoCache;
    private final CatalogoProductosPersistenceService persistencia;

    ProductoServiceImpl(ConsultorCatalogoGestoPago gestoPago, CatalogoProductosCache catalogoCache,
            CatalogoProductosPersistenceService persistencia) {
        this.gestoPago = gestoPago;
        this.catalogoCache = catalogoCache;
        this.persistencia = persistencia;
    }

    @Override
    public CatalogoProductosResponse obtenerProductos() {
        long startedAt = System.nanoTime();
        log.info("Inicia resolución del catálogo para la petición del cliente");
        try {
            CatalogoProductosResponse respuesta = obtenerProductosInterno();
            if (esCodigo(respuesta, CODIGO_PROVEEDOR)) {
                throw new GestoPagoCatalogException("No fue posible obtener un catálogo válido del proveedor");
            }
            if (esCodigo(respuesta, CODIGO_BD)) {
                throw new CatalogoPersistenciaException("No fue posible recuperar o guardar el catálogo");
            }
            return respuesta;
        } finally {
            log.info("Finaliza resolución del catálogo para la petición del cliente: duraciónMs={}",
                    duracionMs(startedAt));
        }
    }

    private CatalogoProductosResponse obtenerProductosInterno() {
        ResultadoLectura lecturaRedis = catalogoCache.obtener();
        if (lecturaRedis.catalogo() != null) {
            CatalogoProductosResponse catalogo = lecturaRedis.catalogo();
            catalogo.setMensaje(MENSAJE_REDIS_EXITOSO);
            log.info("Catálogo resuelto desde Redis: {} productos", catalogo.getProductos().size());
            return catalogo;
        }

        CatalogoProductosResponse catalogoPostgres = leerPostgres();
        if (catalogoPostgres != null) {
            return responderDesdePostgres(catalogoPostgres, lecturaRedis.redisDisponible());
        }

        CatalogoProductosResponse catalogoGestoPago = gestoPago.consultar();
        if (!esCodigo(catalogoGestoPago, CODIGO_OK)) {
            return catalogoGestoPago;
        }
        try {
            return responderDesdePostgres(persistencia.persistirCatalogoRecuperado(catalogoGestoPago.getProductos()),
                    lecturaRedis.redisDisponible());
        } catch (CatalogoPersistenciaException | DataAccessException | TransactionException e) {
            log.error("GestoPago devolvió un catálogo válido, pero PostgreSQL no pudo guardarlo: tipo={}",
                    e.getClass().getSimpleName());
            // No se escribe en Redis: PostgreSQL es el respaldo persistente obligatorio.
            return CatalogoProductosResponse.de(CODIGO_BD, ConsultorCatalogoGestoPago.MENSAJE_ERROR_POSTGRES,
                    List.of());
        }
    }

    private CatalogoProductosResponse leerPostgres() {
        try {
            return persistencia.obtenerCatalogo();
        } catch (CatalogoPersistenciaException | DataAccessException | TransactionException e) {
            log.warn("No se pudo consultar PostgreSQL; se continuará con GestoPago: tipo={}",
                    e.getClass().getSimpleName());
            return null;
        }
    }

    @Override
    public void sincronizarCatalogo() {
        long startedAt = System.nanoTime();
        log.info("Inicia sincronización diaria del catálogo de GestoPago");
        try {
            CatalogoProductosResponse catalogoNuevo = gestoPago.consultar();
            if (!esCodigo(catalogoNuevo, CODIGO_OK)) {
                log.warn("Sincronización omitida: código {} al consultar el catálogo", catalogoNuevo.getCodigo());
                return;
            }

            ResultadoActualizacion resultado = persistencia.actualizarSiHayMasProductos(
                    catalogoNuevo.getProductos());
            if (!resultado.actualizado()) {
                log.info("Se conserva el catálogo actual: nueva cantidad={}, cantidad actual={}",
                        catalogoNuevo.getTotal(), resultado.cantidadAnterior());
                return;
            }

            boolean redisActualizado = catalogoCache.guardar(resultado.catalogoPersistido());
            log.info("Catálogo actualizado en PostgreSQL: {} → {} productos; Redis actualizado={}",
                    resultado.cantidadAnterior(), catalogoNuevo.getTotal(), redisActualizado);
        } catch (CatalogoPersistenciaException | DataAccessException | TransactionException e) {
            log.error("Falló la sincronización del catálogo en PostgreSQL: tipo={}", e.getClass().getSimpleName());
        } finally {
            log.info("Finaliza sincronización diaria del catálogo de GestoPago: duraciónMs={}",
                    duracionMs(startedAt));
        }
    }

    private CatalogoProductosResponse responderDesdePostgres(
            CatalogoProductosResponse catalogo, boolean redisDisponibleAlLeer) {
        log.info("Catálogo resuelto desde PostgreSQL: {} productos", catalogo.getProductos().size());
        boolean cacheActualizada = catalogoCache.guardar(catalogo);
        if (!redisDisponibleAlLeer || !cacheActualizada) {
            return CatalogoProductosResponse.de(3, MENSAJE_REDIS_NO_DISPONIBLE, catalogo.getProductos());
        }
        return catalogo;
    }

    private boolean esCodigo(CatalogoProductosResponse respuesta, int codigo) {
        return Integer.valueOf(codigo).equals(respuesta.getCodigo());
    }

    private long duracionMs(long startedAt) {
        return Duration.ofNanos(System.nanoTime() - startedAt).toMillis();
    }
}
