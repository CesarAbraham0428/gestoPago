package com.proyecto.servicios.service;

import com.proyecto.servicios.entity.sf.Pago;
import com.proyecto.servicios.models.response.gestopago.GestoPagoPagosResponse;
import com.proyecto.servicios.utils.FirmaGestoPago;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Resuelve con confirmTx.do los pagos que quedaron sin resultado (EN_REVISION o PENDIENTE abandonados).
 * GestoPago obliga a llamar a confirmTx cuando sendTx no devolvió resultado, con exactamente los
 * mismos parámetros, al menos 62 s después y dentro de las 24 h siguientes.
 */
@Service
@Slf4j
public class ConciliacionPagoService {

    // confirmTx no debe invocarse antes de 62 s después de sendTx y solo es válido durante 24 h.
    private static final Duration ESPERA_CONFIRMACION = Duration.ofSeconds(62);
    private static final Duration VIGENCIA_CONFIRMACION = Duration.ofHours(24);
    private static final Duration ESPERA_PENDIENTE_ABANDONADO = Duration.ofMinutes(5);

    private final PagoRegistroService registro;
    private final FirmaGestoPago firma;
    private final GestoPagoPagosGateway gateway;
    private final Clock clock;

    @Autowired
    public ConciliacionPagoService(PagoRegistroService registro, FirmaGestoPago firma,
            GestoPagoPagosGateway gateway) {
        this(registro, firma, gateway, Clock.systemUTC());
    }

    ConciliacionPagoService(PagoRegistroService registro, FirmaGestoPago firma, GestoPagoPagosGateway gateway,
            Clock clock) {
        this.registro = registro;
        this.firma = firma;
        this.gateway = gateway;
        this.clock = clock;
    }

    public void conciliarPendientes() {
        Instant ahora = clock.instant();
        List<Integer> ids = registro.idsPorConfirmar(ahora.minus(VIGENCIA_CONFIRMACION),
                ahora.minus(ESPERA_CONFIRMACION), ahora.minus(ESPERA_PENDIENTE_ABANDONADO));
        for (Integer id : ids) {
            try {
                confirmar(id);
            } catch (RuntimeException e) {
                log.error("No se pudo confirmar el pago con GestoPago: folio={}, tipo={}",
                        id, e.getClass().getSimpleName());
            }
        }
    }

    private void confirmar(Integer pagoId) {
        Pago pago = registro.buscarParaConfirmar(pagoId);
        if (pago.getPeticion() == null) {
            log.warn("El pago no tiene la petición original; requiere revisión manual: folio={}", pagoId);
            return;
        }
        GestoPagoPagosResponse respuesta = gateway.confirmar(firma.firmarJson(pago.getPeticion()));

        if (respuesta.esExitoso() || (respuesta.esCodigo(GestoPagoPagosResponse.CODIGO_CONFIRMADO_APLICADO)
                && autorizacionValida(respuesta.getNumAutorizacion()))) {
            registro.confirmarAplicado(pagoId, respuesta);
            log.info("confirmTx: la transacción sí se aplicó: folio={}, idTx={}", pagoId, respuesta.getIdTx());
        } else if (respuesta.esCodigo(GestoPagoPagosResponse.CODIGO_CONFIRMADO_NO_APLICADO)) {
            registro.confirmarNoAplicado(pagoId, respuesta);
            log.info("confirmTx: la transacción no se aplicó; saldo devuelto: folio={}", pagoId);
        } else {
            log.warn("confirmTx sin resultado concluyente; se reintentará: folio={}, codigo={}",
                    pagoId, respuesta.getCodigo());
        }
    }

    private boolean autorizacionValida(String numAutorizacion) {
        return numAutorizacion != null && !numAutorizacion.equals("0") && !numAutorizacion.equals("-1");
    }
}
