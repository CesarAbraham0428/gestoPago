package com.proyecto.servicios.service.Impl;

import com.proyecto.servicios.client.GestoPagoAuthException;
import com.proyecto.servicios.client.GestoPagoCatalogClient;
import com.proyecto.servicios.client.GestoPagoCatalogException;
import com.proyecto.servicios.entity.gestopago.GestoPagoToken;
import com.proyecto.servicios.mapper.ProductoMapper;
import com.proyecto.servicios.models.response.CatalogoProductosResponse;
import com.proyecto.servicios.models.response.ProductoResponse;
import com.proyecto.servicios.models.response.gestopago.GestoPagoCatalogResponse;
import com.proyecto.servicios.service.GestoPagoTokenService;
import com.proyecto.servicios.validation.CatalogoProductosValidator;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.TransactionException;

/**
 * Consulta el catálogo en GestoPago con el token vigente (renovándolo una vez si lo rechaza) y lo valida.
 * Siempre responde un {@link CatalogoProductosResponse}: código 0 correcto, 1 fallo del proveedor y 2 fallo de BD.
 */
@Component
@Slf4j
class ConsultorCatalogoGestoPago {

    static final String MENSAJE_ERROR_POSTGRES =
            "Ha ocurrido un error al consultar o guardar la información del catálogo en la BD";

    private final GestoPagoCatalogClient catalogClient;
    private final GestoPagoTokenService tokenService;
    private final ProductoMapper productoMapper;
    private final Integer idDistribuidor;
    private final String codigoDispositivo;

    ConsultorCatalogoGestoPago(GestoPagoCatalogClient catalogClient, GestoPagoTokenService tokenService,
            ProductoMapper productoMapper,
            @Value("${gestopago.auth.id-distribuidor}") Integer idDistribuidor,
            @Value("${gestopago.auth.codigo-dispositivo}") String codigoDispositivo) {
        this.catalogClient = catalogClient;
        this.tokenService = tokenService;
        this.productoMapper = productoMapper;
        this.idDistribuidor = idDistribuidor;
        this.codigoDispositivo = codigoDispositivo;
    }

    CatalogoProductosResponse consultar() {
        try {
            String token = tokenService.obtenerTokenActivo(idDistribuidor, codigoDispositivo)
                    .map(GestoPagoToken::getToken).filter(valor -> !valor.isBlank()).orElse(null);
            if (token == null) {
                log.error("No hay un token activo de GestoPago para consultar el catálogo");
                return CatalogoProductosResponse.de(1,
                        "No fue posible autenticar la consulta del catálogo de GestoPago", List.of());
            }
            return validar(consultarConRenovacion(token));
        } catch (GestoPagoAuthException | GestoPagoCatalogException e) {
            return CatalogoProductosResponse.de(1, e.getMessage(), List.of());
        } catch (DataAccessException | TransactionException e) {
            log.error("Falló PostgreSQL al consultar o renovar el token de GestoPago: tipo={}",
                    e.getClass().getSimpleName());
            return CatalogoProductosResponse.de(2, MENSAJE_ERROR_POSTGRES, List.of());
        }
    }

    private GestoPagoCatalogResponse consultarConRenovacion(String token) {
        try {
            return catalogClient.consultarCatalogo(token);
        } catch (GestoPagoCatalogException e) {
            if (e.getTipo() != GestoPagoCatalogException.Tipo.AUTENTICACION) {
                throw e;
            }
            log.warn("GestoPago rechazó el token actual; se renovará y se reintentará una vez");
            GestoPagoToken tokenRenovado = tokenService.renovarToken();
            if (tokenRenovado.getToken() == null || tokenRenovado.getToken().isBlank()) {
                throw new GestoPagoAuthException("No fue posible renovar la autenticación de GestoPago",
                        GestoPagoAuthException.Tipo.RESPUESTA_INVALIDA, null, e);
            }
            GestoPagoCatalogResponse catalogo = catalogClient.consultarCatalogo(tokenRenovado.getToken());
            log.info("Consulta de catálogo recuperada después de renovar el token de GestoPago");
            return catalogo;
        }
    }

    private CatalogoProductosResponse validar(GestoPagoCatalogResponse catalogo) {
        if (catalogo == null || catalogo.getMensaje() == null || !"01".equals(catalogo.getMensaje().getCodigo())
                || catalogo.getProductos() == null) {
            return catalogoInvalido();
        }
        List<ProductoResponse> productos = productoMapper.toResponses(catalogo.getProductos());
        if (!CatalogoProductosValidator.esValido(productos)) {
            return catalogoInvalido();
        }
        log.info("Catálogo válido recibido desde GestoPago: {} productos", productos.size());
        return CatalogoProductosResponse.de(0, "Catálogo obtenido correctamente desde GestoPago", productos);
    }

    private CatalogoProductosResponse catalogoInvalido() {
        log.error("GestoPago devolvió un catálogo vacío o con datos incompletos");
        return CatalogoProductosResponse.de(1, "GestoPago no devolvió un catálogo válido", List.of());
    }
}
