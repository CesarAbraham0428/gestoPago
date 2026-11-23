package com.proyecto.servicios.service;

import com.proyecto.servicios.entity.gestopago.Producto;
import com.proyecto.servicios.models.request.PagoServicioRequest;
import com.proyecto.servicios.service.exception.ValidacionException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Set;

/**
 * Decide qué se envía a GestoPago según el tipoFront del producto (reglas de getProductList / sendTx):
 * tipoFront 1 (recargas): el teléfono es el único dato y el precio del catálogo es el precio final.
 * tipoFront 2 (pagos de servicio): referencia y montoPago obligatorios, teléfono fijo "1111111111";
 * aquí el precio del catálogo es la comisión que se cobra al cliente, no el monto.
 * idCatTipoServicio 10 y 11 (artículos canjeables, p. ej. tarjetas de regalo): teléfono fijo "#1111111111".
 */
@Component
@Slf4j
public class ResolutorDestinoPago {

    private static final int TIPO_FRONT_RECARGA = 1;
    private static final int TIPO_FRONT_SERVICIO = 2;
    private static final Set<Integer> TIPOS_SERVICIO_CANJEABLES = Set.of(10, 11);
    private static final String TELEFONO_SERVICIO = "1111111111";
    private static final String TELEFONO_CANJEABLE = "#1111111111";

    private final VerificadorAdeudo adeudo;

    public ResolutorDestinoPago(VerificadorAdeudo adeudo) {
        this.adeudo = adeudo;
    }

    public DestinoPago resolver(Producto producto, PagoServicioRequest request) {
        Integer tipoFront = producto.getTipoFront();
        boolean canjeable = producto.getIdCatTipoServicio() != null
                && TIPOS_SERVICIO_CANJEABLES.contains(producto.getIdCatTipoServicio());
        if (tipoFront != null && tipoFront == TIPO_FRONT_RECARGA) {
            validarPrecioFijo(producto, request.monto());
            if (canjeable) {
                return new DestinoPago(TELEFONO_CANJEABLE, null, null, BigDecimal.ZERO);
            }
            if (request.telefono() == null) {
                throw new ValidacionException("telefono: este producto requiere un número de teléfono de 10 dígitos");
            }
            return new DestinoPago(request.telefono(), null, null, BigDecimal.ZERO);
        }
        if (tipoFront != null && tipoFront == TIPO_FRONT_SERVICIO) {
            if (request.referencia() == null) {
                throw new ValidacionException("referencia: este servicio requiere la referencia del recibo");
            }
            adeudo.validar(producto, request);
            BigDecimal comision = producto.getPrecio() == null
                    ? BigDecimal.ZERO : producto.getPrecio().max(BigDecimal.ZERO);
            return new DestinoPago(canjeable ? TELEFONO_CANJEABLE : TELEFONO_SERVICIO, request.referencia(),
                    request.monto(), comision);
        }
        throw new ValidacionException("idProducto: este producto no se puede pagar");
    }

    /**
     * En recargas GestoPago cobra el precio del catálogo y no el monto de la petición (montoPago va en 0).
     * Sin un precio positivo no hay forma de comprobar que el cargo al cliente sea el real.
     */
    private void validarPrecioFijo(Producto producto, BigDecimal monto) {
        BigDecimal precio = producto.getPrecio();
        if (precio == null || precio.signum() <= 0) {
            log.warn("Producto de recarga sin precio en el catálogo: idProducto={}, idServicio={}",
                    producto.getIdProducto(), producto.getIdServicio());
            throw new ValidacionException("idProducto: este producto no se puede pagar");
        }
        if (precio.compareTo(monto) != 0) {
            throw new ValidacionException("monto: el producto tiene un precio fijo de " + precio.toPlainString());
        }
    }
}
