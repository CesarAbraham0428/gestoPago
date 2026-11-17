package com.proyecto.servicios.service;

import com.proyecto.servicios.entity.gestopago.Producto;
import com.proyecto.servicios.models.request.PagoServicioRequest;
import com.proyecto.servicios.models.request.gestopago.GestoPagoVerificarReferenciaRequest;
import com.proyecto.servicios.models.response.gestopago.GestoPagoPagosResponse;
import com.proyecto.servicios.repositorys.gestopago.ProductoRepository;
import com.proyecto.servicios.service.exception.NegocioException;
import com.proyecto.servicios.service.exception.ValidacionException;
import com.proyecto.servicios.utils.FirmaGestoPago;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Si el servicio tiene producto de consulta (tipoFront 4), verifyReference.do da el adeudo real y el monto
 * debe coincidir con él; así no se paga un monto manipulado. No todos los servicios ofrecen la consulta.
 */
@Component
@Slf4j
public class VerificadorAdeudo {

    private static final int TIPO_FRONT_CONSULTA = 4;

    private final ProductoRepository productos;
    private final FirmaGestoPago firma;
    private final GestoPagoPagosGateway gateway;

    public VerificadorAdeudo(ProductoRepository productos, FirmaGestoPago firma, GestoPagoPagosGateway gateway) {
        this.productos = productos;
        this.firma = firma;
        this.gateway = gateway;
    }

    public void validar(Producto producto, PagoServicioRequest request) {
        Optional<Producto> consulta = productos.findFirstByIdServicioAndTipoFront(producto.getIdServicio(),
                TIPO_FRONT_CONSULTA);
        if (consulta.isEmpty()) {
            return;
        }
        String firmada = firma.firmar(new GestoPagoVerificarReferenciaRequest(consulta.get().getIdProducto(),
                producto.getIdServicio(), request.referencia()));
        GestoPagoPagosResponse respuesta = gateway.verificarReferencia(firmada);
        if (!respuesta.esExitoso() || respuesta.getSaldo() == null) {
            log.warn("verifyReference no confirmó el adeudo: idServicio={}, codigo={}",
                    producto.getIdServicio(), respuesta.getCodigo());
            throw new NegocioException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "No fue posible verificar la referencia; no se realizó ningún cargo");
        }
        if (respuesta.getSaldo().signum() <= 0) {
            throw new NegocioException(HttpStatus.UNPROCESSABLE_ENTITY, "La referencia no tiene adeudo pendiente");
        }
        if (respuesta.getSaldo().compareTo(request.monto()) != 0) {
            throw new ValidacionException("monto: debe ser igual al adeudo de la referencia ("
                    + respuesta.getSaldo().toPlainString() + ")");
        }
    }
}
