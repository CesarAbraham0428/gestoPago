package com.proyecto.servicios.scheduler;

import com.proyecto.servicios.service.ProductoService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class CatalogoProductosScheduler {

    private final ProductoService productoService;

    public CatalogoProductosScheduler(ProductoService productoService) {
        this.productoService = productoService;
    }

    @Scheduled(
            cron = "${gestopago.catalog.sync-cron:0 0 3 * * *}",
            zone = "${gestopago.catalog.sync-zone:America/Mexico_City}")
    public void sincronizarCatalogoDiariamente() {
        try {
            productoService.sincronizarCatalogo();
        } catch (RuntimeException exception) {
            log.error("No se pudo completar la sincronización diaria del catálogo ({})",
                    exception.getClass().getSimpleName());
        }
    }
}
