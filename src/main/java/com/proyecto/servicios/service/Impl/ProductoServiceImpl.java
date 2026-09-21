package com.proyecto.servicios.service.Impl;

import com.proyecto.servicios.cache.CatalogoProductosCache;
import com.proyecto.servicios.cache.CatalogoProductosCache.ResultadoLectura;
import com.proyecto.servicios.client.GestoPagoAuthException;
import com.proyecto.servicios.client.GestoPagoCatalogClient;
import com.proyecto.servicios.client.GestoPagoCatalogException;
import com.proyecto.servicios.entity.gestopago.GestoPagoToken;
import com.proyecto.servicios.model.CatalogoProductosResponse;
import com.proyecto.servicios.model.ProductoResponse;
import com.proyecto.servicios.model.gestopago.GestoPagoCatalogResponse;
import com.proyecto.servicios.model.gestopago.GestoPagoProduct;
import com.proyecto.servicios.service.CatalogoProductosPersistenceService;
import com.proyecto.servicios.service.CatalogoProductosPersistenceService.ResultadoActualizacion;
import com.proyecto.servicios.service.CatalogoPersistenciaException;
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
        long startedAt = System.nanoTime();
        log.info("Inicia resolución del catálogo para la petición del cliente");
        try {
            return obtenerProductosInterno();
        } finally {
            log.info("Finaliza resolución del catálogo para la petición del cliente: duraciónMs={}",
                    java.time.Duration.ofNanos(System.nanoTime() - startedAt).toMillis());
        }
    }

    private CatalogoProductosResponse obtenerProductosInterno() {
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
        } catch (CatalogoPersistenciaException | DataAccessException | TransactionException exception) {
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
            CatalogoProductosResponse persistido = persistencia.persistirCatalogoRecuperado(
                    catalogoGestoPago.getProductos());
            return responderDesdePostgres(persistido, lecturaRedis.redisDisponible());
        } catch (CatalogoPersistenciaException | DataAccessException | TransactionException exception) {
            log.error("GestoPago devolvió un catálogo válido, pero PostgreSQL no pudo guardarlo ({})",
                    exception.getClass().getSimpleName());
            // No se escribe en Redis: PostgreSQL es el respaldo persistente obligatorio.
            return crearRespuesta(2, MENSAJE_ERROR_POSTGRES, List.of());
        }
    }

    @Override
    public void sincronizarCatalogo() {
        long startedAt = System.nanoTime();
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
        } catch (CatalogoPersistenciaException | DataAccessException | TransactionException exception) {
            log.error("Falló la sincronización del catálogo en PostgreSQL ({})",
                    exception.getClass().getSimpleName());
        } finally {
            log.info("Finaliza sincronización diaria del catálogo de GestoPago: duraciónMs={}",
                    java.time.Duration.ofNanos(System.nanoTime() - startedAt).toMillis());
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
        } catch (GestoPagoAuthException exception) {
            log.error("No se pudo obtener un token válido de GestoPago: tipo={}, statusHttp={}",
                    exception.getTipo(), exception.getStatusHttp());
            return crearRespuesta(1, mensajeErrorAutenticacion(exception.getTipo()), List.of());
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
            if (exception.getTipo() != GestoPagoCatalogException.Tipo.AUTENTICACION) {
                log.error("Falló la consulta del catálogo de GestoPago: tipo={}, statusHttp={}",
                        exception.getTipo(), exception.getStatusHttp());
                return crearRespuesta(1, mensajeErrorCatalogo(exception.getTipo()), List.of());
            }

            log.warn("GestoPago rechazó el token actual; se renovará y se reintentará una vez");
            try {
                GestoPagoToken tokenRenovado = tokenService.renovarToken();
                if (tokenRenovado == null || tokenRenovado.getToken() == null
                        || tokenRenovado.getToken().isBlank()) {
                    return crearRespuesta(1, "No fue posible renovar la autenticación de GestoPago", List.of());
                }
                catalogo = catalogClient.consultarCatalogo(tokenRenovado.getToken());
                log.info("Consulta de catálogo recuperada después de renovar el token de GestoPago");
            } catch (GestoPagoAuthException authException) {
                log.error("Falló la renovación del token tras el rechazo de GestoPago: tipo={}, statusHttp={}",
                        authException.getTipo(), authException.getStatusHttp());
                return crearRespuesta(1, mensajeErrorAutenticacion(authException.getTipo()), List.of());
            } catch (GestoPagoCatalogException retryException) {
                log.error("Falló el reintento del catálogo de GestoPago: tipo={}, statusHttp={}",
                        retryException.getTipo(), retryException.getStatusHttp());
                return crearRespuesta(1, mensajeErrorCatalogo(retryException.getTipo()), List.of());
            } catch (DataAccessException | TransactionException persistenceException) {
                log.error("No se pudo persistir el token renovado de GestoPago ({})",
                        persistenceException.getClass().getSimpleName());
                return crearRespuesta(2, MENSAJE_ERROR_POSTGRES, List.of());
            }
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

    private String mensajeErrorCatalogo(GestoPagoCatalogException.Tipo tipo) {
        return switch (tipo) {
            case AUTENTICACION -> "GestoPago rechazó la autenticación del catálogo";
            case TIMEOUT -> "Se agotó el tiempo de espera al consultar GestoPago";
            case HTTP -> "GestoPago devolvió una respuesta HTTP no exitosa";
            case COMUNICACION -> "No hay conexión al servicio de GestoPago";
            case XML -> "La respuesta XML de GestoPago no es válida";
            case RESPUESTA_VACIA -> "GestoPago devolvió una respuesta vacía";
            case RESPUESTA_INVALIDA -> "GestoPago no devolvió un catálogo válido";
        };
    }

    private String mensajeErrorAutenticacion(GestoPagoAuthException.Tipo tipo) {
        return switch (tipo) {
            case AUTENTICACION -> "GestoPago rechazó las credenciales de autenticación";
            case TIMEOUT -> "Se agotó el tiempo de espera al autenticar con GestoPago";
            case HTTP -> "GestoPago devolvió un error HTTP durante la autenticación";
            case COMUNICACION -> "No hay conexión al servicio de autenticación de GestoPago";
            case RESPUESTA_INVALIDA -> "GestoPago no devolvió un token válido";
        };
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
