package com.proyecto.servicios.scheduler;

import com.proyecto.servicios.client.GestoPagoAuthException;
import com.proyecto.servicios.service.GestoPagoTokenService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.TransactionException;

/** Mantiene vigente el token de GestoPago: revisión periódica y renovación diaria. */
@Component
@Slf4j
public class GestoPagoTokenScheduler {

    private final GestoPagoTokenService tokens;

    public GestoPagoTokenScheduler(GestoPagoTokenService tokens) {
        this.tokens = tokens;
    }

    @Scheduled(fixedDelayString = "${gestopago.auth.renewal-check-ms:60000}", initialDelay = 0)
    public void revisarRenovacion() {
        log.info("Inicia revisión programada del token de GestoPago");
        ejecutar("revisión programada", tokens::renovarSiHaceFalta);
    }

    /**
     * El token de GestoPago dura 24 horas: se renueva todos los días a las 00:00 (hora de CDMX)
     * aunque siga vigente. La revisión periódica cubre los reinicios y las fallas de este cron.
     */
    @Scheduled(cron = "${gestopago.auth.renewal-cron:0 0 0 * * *}",
            zone = "${gestopago.auth.renewal-zone:America/Mexico_City}")
    public void renovarDiariamente() {
        log.info("Inicia renovación diaria del token de GestoPago");
        ejecutar("renovación diaria", tokens::renovarToken);
    }

    private void ejecutar(String tarea, Runnable accion) {
        try {
            accion.run();
        } catch (GestoPagoAuthException exception) {
            log.error("Falló la {} del token de GestoPago: tipo={}, statusHttp={}",
                    tarea, exception.getTipo(), exception.getStatusHttp());
        } catch (DataAccessException | TransactionException exception) {
            log.error("Falló PostgreSQL durante la {} del token ({})", tarea, exception.getClass().getSimpleName());
        }
    }
}
