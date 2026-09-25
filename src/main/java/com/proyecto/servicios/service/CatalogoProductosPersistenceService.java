package com.proyecto.servicios.service;

import com.proyecto.servicios.entity.gestopago.Producto;
import com.proyecto.servicios.mapper.ProductoMapper;
import com.proyecto.servicios.model.CatalogoProductosResponse;
import com.proyecto.servicios.model.ProductoResponse;
import com.proyecto.servicios.repositorys.gestopago.ProductoRepository;
import com.proyecto.servicios.validation.CatalogoProductosValidator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.TransactionException;

import java.time.Duration;
import java.util.List;

@Service
@Slf4j
public class CatalogoProductosPersistenceService {

    private ProductoRepository productoRepository;
    private ProductoMapper productoMapper;

    @Autowired
    public CatalogoProductosPersistenceService(ProductoRepository productoRepository, ProductoMapper productoMapper) {
        this.productoRepository = productoRepository;
        this.productoMapper = productoMapper;
    }

    @Transactional(readOnly = true, transactionManager = "sfTransactionManager")

    public CatalogoProductosResponse obtenerCatalogo() {
        long startedAt = System.nanoTime();
        log.info("Inicia lectura del catálogo en PostgreSQL");
        try {
            List<Producto> productos = productoRepository.findAllByOrderByIdAsc();
            if (productos.isEmpty()) {
                log.info("PostgreSQL no contiene productos");
                return null;
            }
            CatalogoProductosResponse catalogo = crearRespuesta(productos);
            if (!CatalogoProductosValidator.esValido(catalogo.getProductos())) {
                log.error("PostgreSQL contiene un catálogo incompleto; se intentará la fuente externa");
                return null;
            }
            log.info("PostgreSQL devolvió un catálogo válido: {} productos", productos.size());
            return catalogo;
        } catch (DataAccessException | TransactionException exception) {
            log.error("Falló la consulta del catálogo en PostgreSQL ({})",
                    exception.getClass().getSimpleName());
            throw exception;
        } finally {
            log.info("Finaliza lectura del catálogo en PostgreSQL: duraciónMs={}", duracionMs(startedAt));
        }
    }

    @Transactional(transactionManager = "sfTransactionManager")
    
    public ResultadoActualizacion actualizarSiHayMasProductos(List<ProductoResponse> productosNuevos) {
        long startedAt = System.nanoTime();
        int cantidadNueva = productosNuevos == null ? 0 : productosNuevos.size();
        log.info("Inicia actualización del catálogo en PostgreSQL: productosNuevos={}", cantidadNueva);
        try {
            if (!CatalogoProductosValidator.esValido(productosNuevos)) {
                long cantidadActual = productoRepository.count();
                log.warn("Se rechazó un catálogo vacío o incompleto; PostgreSQL no será modificado");
                return new ResultadoActualizacion(false, cantidadActual, null);
            }

            productoRepository.bloquearEscriturasCatalogo();
            long cantidadActual = productoRepository.count();
            if (cantidadActual > 0 && productosNuevos.size() <= cantidadActual) {
                List<Producto> actuales = productoRepository.findAllByOrderByIdAsc();
                if (!actuales.isEmpty()) {
                    CatalogoProductosResponse vigente = crearRespuesta(actuales);
                    log.info("Se conserva el catálogo: cantidadNueva={}, cantidadActual={}",
                            productosNuevos.size(), cantidadActual);
                    return new ResultadoActualizacion(false, cantidadActual,
                            CatalogoProductosValidator.esValido(vigente.getProductos()) ? vigente : null);
                }
            }

            CatalogoProductosResponse respuesta = reemplazarCatalogo(productosNuevos);
            log.info("Catálogo válido escrito en PostgreSQL: {} → {} productos", cantidadActual, cantidadNueva);
            return new ResultadoActualizacion(true, cantidadActual, respuesta);
        } catch (DataAccessException | TransactionException exception) {
            log.error("Falló la actualización del catálogo en PostgreSQL ({})",
                    exception.getClass().getSimpleName());
            throw exception;
        } finally {
            log.info("Finaliza actualización del catálogo en PostgreSQL: duraciónMs={}", duracionMs(startedAt));
        }
    }

    @Transactional(transactionManager = "sfTransactionManager")
    public CatalogoProductosResponse persistirCatalogoRecuperado(List<ProductoResponse> productos) {
        long startedAt = System.nanoTime();
        int cantidad = productos == null ? 0 : productos.size();
        log.info("Inicia persistencia del catálogo recuperado de GestoPago: productos={}", cantidad);
        try {
            if (!CatalogoProductosValidator.esValido(productos)) {
                throw new CatalogoPersistenciaException(
                        "Se intentó persistir un catálogo vacío o incompleto de GestoPago");
            }
            productoRepository.bloquearEscriturasCatalogo();
            long cantidadAnterior = productoRepository.count();
            CatalogoProductosResponse persistido = reemplazarCatalogo(productos);
            log.info("Catálogo de GestoPago persistido en PostgreSQL: {} → {} productos",
                    cantidadAnterior, cantidad);
            return persistido;
        } catch (DataAccessException | TransactionException exception) {
            log.error("Falló la persistencia del catálogo recuperado de GestoPago en PostgreSQL ({})",
                    exception.getClass().getSimpleName());
            throw exception;
        } finally {
            log.info("Finaliza persistencia del catálogo recuperado de GestoPago: duraciónMs={}",
                    duracionMs(startedAt));
        }
    }

    private CatalogoProductosResponse reemplazarCatalogo(List<ProductoResponse> productos) {
        List<Producto> entidadesNuevas = productoMapper.toEntities(productos);

        // El borrado y la inserción forman una sola transacción; un fallo revierte ambos pasos.
        productoRepository.deleteAllInBatch();
        productoRepository.saveAllAndFlush(entidadesNuevas);

        // Volver a leer para devolver exactamente los valores aceptados por los tipos de PostgreSQL.
        List<Producto> persistidos = productoRepository.findAllByOrderByIdAsc();
        CatalogoProductosResponse respuesta = crearRespuesta(persistidos);
        if (!CatalogoProductosValidator.esValido(respuesta.getProductos())) {
            log.error("PostgreSQL no conservó un catálogo válido después de la escritura");
            throw new CatalogoPersistenciaException(
                    "PostgreSQL no conservó un catálogo válido después de la escritura");
        }
        return respuesta;
    }

    private CatalogoProductosResponse crearRespuesta(List<Producto> entidades) {
        CatalogoProductosResponse response = new CatalogoProductosResponse();
        response.setCodigo(0);
        response.setMensaje("Catálogo obtenido correctamente desde PostgreSQL");
        response.setProductos(entidades.stream().map(productoMapper::toResponse).toList());
        response.setTotal(response.getProductos().size());
        return response;
    }

    private long duracionMs(long startedAt) {
        return Duration.ofNanos(System.nanoTime() - startedAt).toMillis();
    }

    public record ResultadoActualizacion(
            boolean actualizado,
            long cantidadAnterior,
            CatalogoProductosResponse catalogoPersistido) {
    }
}
