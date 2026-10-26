package com.proyecto.servicios.scheduler;

import com.proyecto.servicios.service.PagoService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Resuelve con confirmTx.do los pagos que quedaron sin respuesta de sendTx.do. */
@Component
@Slf4j
public class PagoConciliacionScheduler {

    private final PagoService pagoService;

    public PagoConciliacionScheduler(PagoService pagoService) {
        this.pagoService = pagoService;
    }

    @Scheduled(fixedDelayString = "${gestopago.pagos.conciliacion-ms:60000}",
            initialDelayString = "${gestopago.pagos.conciliacion-ms:60000}")
    public void conciliarPagosPendientes() {
        try {
            pagoService.conciliarPendientes();
        } catch (RuntimeException exception) {
            log.error("No se pudo completar la conciliación de pagos ({})", exception.getClass().getSimpleName());
        }
    }
}
