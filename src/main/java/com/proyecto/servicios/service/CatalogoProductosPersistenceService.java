package com.proyecto.servicios.service;

import com.proyecto.servicios.entity.gestopago.Producto;
import com.proyecto.servicios.model.CatalogoProductosResponse;
import com.proyecto.servicios.model.ProductoResponse;
import com.proyecto.servicios.repositorys.gestopago.ProductoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class CatalogoProductosPersistenceService {

    private final ProductoRepository productoRepository;

    public CatalogoProductosPersistenceService(ProductoRepository productoRepository) {
        this.productoRepository = productoRepository;
    }

    @Transactional(readOnly = true, transactionManager = "sfTransactionManager")
    public Optional<CatalogoProductosResponse> obtenerCatalogo() {
        List<Producto> productos = productoRepository.findAllByOrderByIdAsc();
        if (productos.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(crearRespuesta(productos));
    }

    @Transactional(transactionManager = "sfTransactionManager")
    public ResultadoActualizacion actualizarSiHayMasProductos(List<ProductoResponse> productosNuevos) {
        if (productosNuevos == null || productosNuevos.isEmpty()) {
            return new ResultadoActualizacion(false, productoRepository.count(), null);
        }

        long cantidadActual = productoRepository.count();
        if (cantidadActual > 0 && productosNuevos.size() <= cantidadActual) {
            List<Producto> actuales = productoRepository.findAllByOrderByIdAsc();
            if (!actuales.isEmpty()) {
                return new ResultadoActualizacion(false, cantidadActual, crearRespuesta(actuales));
            }
        }

        List<Producto> entidadesNuevas = productosNuevos.stream()
                .map(this::crearEntidad)
                .toList();

        // El borrado y la inserción forman una sola transacción; un fallo revierte ambos pasos.
        productoRepository.deleteAllInBatch();
        productoRepository.saveAllAndFlush(entidadesNuevas);

        // Volver a leer para devolver exactamente los valores aceptados por los tipos de PostgreSQL.
        List<Producto> persistidos = productoRepository.findAllByOrderByIdAsc();
        return new ResultadoActualizacion(true, cantidadActual, crearRespuesta(persistidos));
    }

    private Producto crearEntidad(ProductoResponse response) {
        Producto producto = new Producto();
        producto.setProducto(response.getProducto());
        producto.setServicio(response.getServicio());
        producto.setIdServicio(response.getIdServicio());
        producto.setIdProducto(response.getIdProducto());
        producto.setIdCatTipoServicio(response.getIdCatTipoServicio());
        producto.setTipoFront(response.getTipoFront());
        producto.setHasDigitoVerificador(response.getHasDigitoVerificador());
        producto.setTipoReferencia(response.getTipoReferencia());
        producto.setPrecio(response.getPrecio());
        producto.setShowAyuda(response.getShowAyuda());
        producto.setLegend(response.getLegend());
        return producto;
    }

    private CatalogoProductosResponse crearRespuesta(List<Producto> entidades) {
        CatalogoProductosResponse response = new CatalogoProductosResponse();
        response.setCodigo(0);
        response.setMensaje("Catálogo obtenido correctamente desde PostgreSQL");
        response.setProductos(entidades.stream().map(this::crearResponse).toList());
        response.setTotal(response.getProductos().size());
        return response;
    }

    private ProductoResponse crearResponse(Producto entidad) {
        ProductoResponse response = new ProductoResponse();
        response.setProducto(entidad.getProducto());
        response.setServicio(entidad.getServicio());
        response.setIdServicio(entidad.getIdServicio());
        response.setIdProducto(entidad.getIdProducto());
        response.setIdCatTipoServicio(entidad.getIdCatTipoServicio());
        response.setTipoFront(entidad.getTipoFront());
        response.setHasDigitoVerificador(entidad.getHasDigitoVerificador());
        response.setTipoReferencia(entidad.getTipoReferencia());
        response.setPrecio(entidad.getPrecio());
        response.setShowAyuda(entidad.getShowAyuda());
        response.setLegend(entidad.getLegend());
        return response;
    }

    public record ResultadoActualizacion(
            boolean actualizado,
            long cantidadAnterior,
            CatalogoProductosResponse catalogoPersistido) {
    }
}
