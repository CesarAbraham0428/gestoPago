package com.proyecto.servicios.service.Impl;

import com.proyecto.servicios.cache.CatalogoProductosCache;
import com.proyecto.servicios.cache.CatalogoProductosCache.ResultadoLectura;
import com.proyecto.servicios.client.GestoPagoCatalogClient;
import com.proyecto.servicios.client.GestoPagoCatalogException;
import com.proyecto.servicios.entity.gestopago.GestoPagoToken;
import com.proyecto.servicios.model.CatalogoProductosResponse;
import com.proyecto.servicios.model.ProductoResponse;
import com.proyecto.servicios.model.gestopago.GestoPagoCatalogResponse;
import com.proyecto.servicios.model.gestopago.GestoPagoProduct;
import com.proyecto.servicios.service.CatalogoProductosPersistenceService;
import com.proyecto.servicios.service.CatalogoProductosPersistenceService.ResultadoActualizacion;
import com.proyecto.servicios.service.GestoPagoTokenService;
import com.proyecto.servicios.service.ProductoService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionException;

import java.util.List;
import java.util.Optional;

@Service
@Slf4j
public class ProductoServiceImpl implements ProductoService {

    private static final String MENSAJE_ERROR_POSTGRES =
            "Ha ocurrido un error al consultar o guardar la información del catálogo en la BD";
    private static final String MENSAJE_REDIS_NO_DISPONIBLE =
            "Se ha consultado correctamente la información del catálogo de la BD";
    private static final String MENSAJE_REDIS_EXITOSO =
            "Catálogo obtenido correctamente desde Redis";

    private final GestoPagoCatalogClient catalogClient;
    private final CatalogoProductosCache catalogoCache;
    private final CatalogoProductosPersistenceService persistencia;
    private final GestoPagoTokenService tokenService;
    private final Integer idDistribuidor;
    private final String codigoDispositivo;

    public ProductoServiceImpl(
            GestoPagoCatalogClient catalogClient,
            CatalogoProductosCache catalogoCache,
            CatalogoProductosPersistenceService persistencia,
            GestoPagoTokenService tokenService,
            @Value("${gestopago.auth.id-distribuidor}") Integer idDistribuidor,
            @Value("${gestopago.auth.codigo-dispositivo}") String codigoDispositivo) {
        this.catalogClient = catalogClient;
        this.catalogoCache = catalogoCache;
        this.persistencia = persistencia;
        this.tokenService = tokenService;
        this.idDistribuidor = idDistribuidor;
        this.codigoDispositivo = codigoDispositivo;
    }

    @Override
    public CatalogoProductosResponse obtenerProductos() {
        ResultadoLectura lecturaRedis = catalogoCache.obtener();
        if (lecturaRedis.catalogo().isPresent()) {
            CatalogoProductosResponse catalogo = lecturaRedis.catalogo().get();
            catalogo.setMensaje(MENSAJE_REDIS_EXITOSO);
            log.info("Catálogo resuelto desde Redis: {} productos", catalogo.getProductos().size());
            return catalogo;
        }

        Optional<CatalogoProductosResponse> catalogoPostgres;
        try {
            catalogoPostgres = persistencia.obtenerCatalogo();
        } catch (DataAccessException | TransactionException exception) {
            log.warn("No se pudo consultar PostgreSQL; se continuará con GestoPago ({})",
                    exception.getClass().getSimpleName());
            catalogoPostgres = Optional.empty();
        }

        if (catalogoPostgres.isPresent()) {
            return responderDesdePostgres(catalogoPostgres.get(), lecturaRedis.redisDisponible());
        }

        CatalogoProductosResponse catalogoGestoPago = consultarCatalogoGestoPago();
        if (!Integer.valueOf(0).equals(catalogoGestoPago.getCodigo())) {
            return catalogoGestoPago;
        }

        try {
            ResultadoActualizacion resultado = persistencia.actualizarSiHayMasProductos(
                    catalogoGestoPago.getProductos());
            if (resultado.catalogoPersistido() == null) {
                return crearRespuesta(2, MENSAJE_ERROR_POSTGRES, List.of());
            }
            return responderDesdePostgres(resultado.catalogoPersistido(), lecturaRedis.redisDisponible());
        } catch (DataAccessException | TransactionException exception) {
            log.error("GestoPago devolvió un catálogo válido, pero PostgreSQL no pudo guardarlo ({})",
                    exception.getClass().getSimpleName());
            // No se escribe en Redis: PostgreSQL es el respaldo persistente obligatorio.
            return crearRespuesta(2, MENSAJE_ERROR_POSTGRES, List.of());
        }
    }

    @Override
    public void sincronizarCatalogo() {
        log.info("Inicia sincronización diaria del catálogo de GestoPago");
        try {
            CatalogoProductosResponse catalogoNuevo = consultarCatalogoGestoPago();
            if (!Integer.valueOf(0).equals(catalogoNuevo.getCodigo())) {
                if (Integer.valueOf(2).equals(catalogoNuevo.getCodigo())) {
                    log.error("Sincronización omitida por un error de PostgreSQL");
                } else {
                    log.warn("Sincronización omitida: GestoPago no devolvió un catálogo válido");
                }
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
        } catch (DataAccessException | TransactionException exception) {
            log.error("Falló la sincronización del catálogo en PostgreSQL ({})",
                    exception.getClass().getSimpleName());
        } finally {
            log.info("Finaliza sincronización diaria del catálogo de GestoPago");
        }
    }

    private CatalogoProductosResponse responderDesdePostgres(
            CatalogoProductosResponse catalogo, boolean redisDisponibleAlLeer) {
        log.info("Catálogo resuelto desde PostgreSQL: {} productos", catalogo.getProductos().size());
        boolean cacheActualizada = catalogoCache.guardar(catalogo);
        if (!redisDisponibleAlLeer || !cacheActualizada) {
            return crearRespuesta(3, MENSAJE_REDIS_NO_DISPONIBLE, catalogo.getProductos());
        }
        return catalogo;
    }

    private CatalogoProductosResponse consultarCatalogoGestoPago() {
        Optional<GestoPagoToken> token;
        try {
            token = tokenService.obtenerTokenActivo(idDistribuidor, codigoDispositivo);
        } catch (DataAccessException | TransactionException exception) {
            log.error("No fue posible consultar el token de GestoPago en PostgreSQL ({})",
                    exception.getClass().getSimpleName());
            return crearRespuesta(2, MENSAJE_ERROR_POSTGRES, List.of());
        }

        if (token.isEmpty() || token.get().getToken() == null || token.get().getToken().isBlank()) {
            log.error("No hay un token activo de GestoPago para consultar el catálogo");
            return crearRespuesta(1, "No fue posible autenticar la consulta del catálogo de GestoPago", List.of());
        }

        GestoPagoCatalogResponse catalogo;
        try {
            catalogo = catalogClient.consultarCatalogo(token.get().getToken());
        } catch (GestoPagoCatalogException exception) {
            log.error("Falló la consulta del catálogo de GestoPago ({})", exception.getClass().getSimpleName());
            return crearRespuesta(1, "No fue posible obtener el catálogo de GestoPago", List.of());
        }

        if (catalogo == null
                || catalogo.getMensaje() == null
                || !"01".equals(catalogo.getMensaje().getCodigo())
                || catalogo.getProductos() == null
                || catalogo.getProductos().isEmpty()
                || catalogo.getProductos().stream().anyMatch(producto -> !productoValido(producto))) {
            log.error("GestoPago devolvió un catálogo vacío o con datos incompletos");
            return crearRespuesta(1, "GestoPago no devolvió un catálogo válido", List.of());
        }

        List<ProductoResponse> productos = catalogo.getProductos().stream()
                .map(this::mapearProducto)
                .toList();
        log.info("Catálogo válido recibido desde GestoPago: {} productos", productos.size());
        return crearRespuesta(0, "Catálogo obtenido correctamente desde GestoPago", productos);
    }

    private boolean productoValido(GestoPagoProduct producto) {
        return producto != null
                && producto.getProducto() != null && !producto.getProducto().isBlank()
                && producto.getServicio() != null && !producto.getServicio().isBlank()
                && producto.getIdServicio() != null
                && producto.getIdProducto() != null;
    }

    private CatalogoProductosResponse crearRespuesta(
            Integer codigo, String mensaje, List<ProductoResponse> productos) {
        CatalogoProductosResponse response = new CatalogoProductosResponse();
        response.setCodigo(codigo);
        response.setMensaje(mensaje);
        response.setProductos(productos);
        response.setTotal(productos.size());
        return response;
    }

    private ProductoResponse mapearProducto(GestoPagoProduct producto) {
        ProductoResponse response = new ProductoResponse();
        response.setProducto(producto.getProducto());
        response.setServicio(producto.getServicio());
        response.setIdServicio(producto.getIdServicio());
        response.setIdProducto(producto.getIdProducto());
        response.setIdCatTipoServicio(producto.getIdCatTipoServicio());
        response.setTipoFront(producto.getTipoFront());
        response.setHasDigitoVerificador(producto.getHasDigitoVerificador());
        response.setTipoReferencia(producto.getTipoReferencia());
        response.setPrecio(producto.getPrecio());
        response.setShowAyuda(producto.getShowAyuda());
        response.setLegend(producto.getLegend());
        return response;
    }
}
