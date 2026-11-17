package com.proyecto.servicios.client;

import feign.RequestInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;

/**
 * Configuración exclusiva del cliente de pagos (sin @Configuration para no aplicarla a los demás clientes).
 * GestoPago pide la cabecera X-API-Key solo si asignó una al distribuidor.
 */
public class GestoPagoPagosFeignConfig {

    @Bean
    RequestInterceptor gestoPagoApiKeyInterceptor(@Value("${gestopago.pagos.api-key:}") String apiKey) {
        return template -> {
            if (apiKey != null && !apiKey.isBlank()) {
                template.header("X-API-Key", apiKey.strip());
            }
        };
    }
}
