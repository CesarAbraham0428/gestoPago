package com.proyecto.servicios.utils;

import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Contador por ventana fija en Redis, compartido entre instancias de la API. Si Redis no responde deja pasar
 * la solicitud: los demás controles (bloqueo del PIN, intentos por código) siguen protegiendo en la BD.
 */
@Component
@Slf4j
public class LimitadorVentanaFija {

    private final StringRedisTemplate redis;

    public LimitadorVentanaFija(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /** Registra una solicitud y dice si con ella se superó el máximo permitido en la ventana. */
    public boolean excede(String clave, int maximo, Duration ventana) {
        try {
            Long solicitudes = redis.opsForValue().increment(clave);
            if (solicitudes != null && solicitudes == 1) {
                redis.expire(clave, ventana);
            }
            return solicitudes != null && solicitudes > maximo;
        } catch (DataAccessException e) {
            log.warn("Redis no disponible; no se aplicó el límite de solicitudes ({})",
                    e.getClass().getSimpleName());
            return false;
        }
    }
}
