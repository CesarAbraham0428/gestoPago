package com.proyecto.servicios.client;

import com.proyecto.servicios.service.EnviadorCodigo;
import java.time.Duration;
import lombok.extern.slf4j.Slf4j;

/** SOLO DESARROLLO: escribe el código en el log. Se activa únicamente con gestopago.app.2fa.consola=true. */
@Slf4j
public class EnviadorCodigoConsola implements EnviadorCodigo {

    @Override
    public void enviar(String destinatario, String codigo, Duration vigencia) {
        log.warn("[2FA solo desarrollo] Código para {}: {} (vence en {} min)", destinatario, codigo,
                vigencia.toMinutes());
    }
}
