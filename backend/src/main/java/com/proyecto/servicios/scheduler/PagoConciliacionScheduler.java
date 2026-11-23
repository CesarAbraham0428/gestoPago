package com.proyecto.servicios.scheduler;

import com.proyecto.servicios.service.ConciliacionPagoService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Resuelve con confirmTx.do los pagos que quedaron sin respuesta de sendTx.do. */
@Component
@Slf4j
public class PagoConciliacionScheduler {

    private final ConciliacionPagoService conciliacion;

    public PagoConciliacionScheduler(ConciliacionPagoService conciliacion) {
        this.conciliacion = conciliacion;
    }

    @Scheduled(fixedDelayString = "${gestopago.pagos.conciliacion-ms:60000}",
            initialDelayString = "${gestopago.pagos.conciliacion-ms:60000}")
    public void conciliarPagosPendientes() {
        try {
            conciliacion.conciliarPendientes();
        } catch (RuntimeException exception) {
            log.error("No se pudo completar la conciliación de pagos ({})", exception.getClass().getSimpleName());
        }
    }
}
