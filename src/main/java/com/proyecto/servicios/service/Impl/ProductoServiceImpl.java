package com.proyecto.servicios.service.Impl;

import com.proyecto.servicios.client.GestoPagoCatalogClient;
import com.proyecto.servicios.client.GestoPagoCatalogException;
import com.proyecto.servicios.entity.gestopago.GestoPagoToken;
import com.proyecto.servicios.model.CatalogoProductosResponse;
import com.proyecto.servicios.model.ProductoResponse;
import com.proyecto.servicios.model.gestopago.GestoPagoCatalogResponse;
import com.proyecto.servicios.model.gestopago.GestoPagoProduct;
import com.proyecto.servicios.service.GestoPagoTokenService;
import com.proyecto.servicios.service.ProductoService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

@Service
@Slf4j
public class ProductoServiceImpl implements ProductoService {

    private final GestoPagoCatalogClient catalogClient;
    private final GestoPagoTokenService tokenService;
    private final Integer idDistribuidor;
    private final String codigoDispositivo;

    public ProductoServiceImpl(
            GestoPagoCatalogClient catalogClient,
            GestoPagoTokenService tokenService,
            @Value("${gestopago.auth.id-distribuidor}") Integer idDistribuidor,
            @Value("${gestopago.auth.codigo-dispositivo}") String codigoDispositivo) {
        this.catalogClient = catalogClient;
        this.tokenService = tokenService;
        this.idDistribuidor = idDistribuidor;
        this.codigoDispositivo = codigoDispositivo;
    }

    @Override
    public CatalogoProductosResponse obtenerProductos() {
        Optional<GestoPagoToken> token = tokenService.obtenerTokenActivo(idDistribuidor, codigoDispositivo);
        if (token.isEmpty() || token.get().getToken() == null || token.get().getToken().isBlank()) {
            log.error("No hay un token activo de GestoPago para consultar el catálogo");
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "No fue posible autenticar la consulta del catálogo de GestoPago"
            );
        }

        GestoPagoCatalogResponse catalogo;
        try {
            catalogo = catalogClient.consultarCatalogo(token.get().getToken());
        } catch (GestoPagoCatalogException exception) {
            log.error("Falló la consulta del catálogo de GestoPago ({})", exception.getClass().getSimpleName());
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "No fue posible obtener el catálogo de GestoPago"
            );
        }

        if (catalogo == null
                || catalogo.getMensaje() == null
                || !"01".equals(catalogo.getMensaje().getCodigo())
                || catalogo.getProductos() == null
                || catalogo.getProductos().isEmpty()) {
            log.error("GestoPago devolvió un catálogo vacío o con código de operación no exitoso");
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "GestoPago no devolvió un catálogo válido"
            );
        }

        CatalogoProductosResponse response = new CatalogoProductosResponse();
        response.setCodigo(0);
        response.setMensaje("Catálogo obtenido correctamente");
        response.setProductos(catalogo.getProductos().stream().map(this::mapearProducto).toList());
        response.setTotal(response.getProductos().size());
        log.info("Catálogo de GestoPago obtenido correctamente: {} productos", response.getProductos().size());
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