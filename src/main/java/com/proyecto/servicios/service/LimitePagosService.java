package com.proyecto.servicios.service;

import com.proyecto.servicios.service.exception.NegocioException;
import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * Limita las solicitudes de pago por usuario con un contador en Redis (ventana fija de 60 s), compartido entre
 * instancias de la API. Si Redis no responde se deja pasar la solicitud: el bloqueo del PIN sigue protegiendo
 * en la BD y una caída de Redis no debe detener los pagos.
 */
@Service
@Slf4j
public class LimitePagosService {

    private static final Duration VENTANA = Duration.ofMinutes(1);

    private final StringRedisTemplate redis;
    private final int maximoPorVentana;

    public LimitePagosService(StringRedisTemplate redis,
            @Value("${gestopago.pagos.limite-por-minuto:5}") int maximoPorVentana) {
        this.redis = redis;
        this.maximoPorVentana = maximoPorVentana;
    }

    public void verificar(Integer usuarioId) {
        String clave = "limite:pagos:" + usuarioId;
        Long solicitudes;
        try {
            solicitudes = redis.opsForValue().increment(clave);
            if (solicitudes != null && solicitudes == 1) {
                redis.expire(clave, VENTANA);
            }
        } catch (DataAccessException e) {
            log.warn("Redis no disponible; no se aplicó el límite de pagos ({})", e.getClass().getSimpleName());
            return;
        }
        if (solicitudes != null && solicitudes > maximoPorVentana) {
            throw new NegocioException(HttpStatus.TOO_MANY_REQUESTS,
                    "Demasiadas solicitudes de pago; intenta de nuevo en un minuto");
        }
    }
}
