package com.proyecto.servicios.utils;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Contador por ventana fija en Redis, compartido entre instancias de la API. Si Redis no responde (caído o sin
 * credenciales válidas) NO se deja pasar sin límite: se cuenta en memoria de este proceso, de modo que la fuerza
 * bruta sigue frenada aunque sea por instancia.
 */
@Component
@Slf4j
public class LimitadorVentanaFija {

    /** Resultado de contar una solicitud: si excedió el máximo y cuántos segundos faltan para reintentar. */
    public record Resultado(boolean excedido, long reintentarEnSegundos) {
        public static final Resultado PERMITIDO = new Resultado(false, 0);
    }

    private static final int MAX_CLAVES_LOCALES = 100_000;
    private static final long AVISO_CADA_MS = 60_000;

    private static final class Contador {
        final long vence;
        int cuenta;

        Contador(long vence) {
            this.vence = vence;
        }
    }

    private final StringRedisTemplate redis;
    private final LongSupplier reloj;
    private final ConcurrentHashMap<String, Contador> local = new ConcurrentHashMap<>();
    private final AtomicLong ultimoAviso = new AtomicLong();

    // Con dos constructores Spring exige indicar cuál usar; el otro solo existe para inyectar un reloj en pruebas.
    @Autowired
    public LimitadorVentanaFija(StringRedisTemplate redis) {
        this(redis, System::currentTimeMillis);
    }

    LimitadorVentanaFija(StringRedisTemplate redis, LongSupplier reloj) {
        this.redis = redis;
        this.reloj = reloj;
    }

    /** Registra una solicitud y dice si con ella se superó el máximo permitido en la ventana. */
    public boolean excede(String clave, int maximo, Duration ventana) {
        return registrar(clave, maximo, ventana).excedido();
    }

    /** Igual que {@link #excede}, pero también informa cuánto falta para que la ventana se reinicie. */
    public Resultado registrar(String clave, int maximo, Duration ventana) {
        try {
            Long solicitudes = redis.opsForValue().increment(clave);
            if (solicitudes != null) {
                return resultadoRedis(clave, solicitudes, maximo, ventana);
            }
        } catch (DataAccessException e) {
            avisarRespaldo(e);
        }
        return registrarLocal(clave, maximo, ventana);
    }

    private Resultado resultadoRedis(String clave, long solicitudes, int maximo, Duration ventana) {
        if (solicitudes == 1) {
            redis.expire(clave, ventana);
        }
        if (solicitudes <= maximo) {
            return Resultado.PERMITIDO;
        }
        Long restante = redis.getExpire(clave, TimeUnit.SECONDS);
        if (restante == null || restante < 0) {
            // Un contador sin vencimiento (el proceso murió entre INCR y EXPIRE) bloquearía para siempre.
            redis.expire(clave, ventana);
            restante = ventana.toSeconds();
        }
        return new Resultado(true, Math.max(1, restante));
    }

    private Resultado registrarLocal(String clave, int maximo, Duration ventana) {
        long ahora = reloj.getAsLong();
        if (local.size() > MAX_CLAVES_LOCALES) {
            local.values().removeIf(c -> ahora >= c.vence);
            if (local.size() > MAX_CLAVES_LOCALES) {
                local.clear(); // inundación de claves: es preferible reiniciar contadores que agotar la memoria
            }
        }
        Contador contador = local.compute(clave, (k, actual) -> {
            Contador c = actual == null || ahora >= actual.vence ? new Contador(ahora + ventana.toMillis()) : actual;
            c.cuenta++;
            return c;
        });
        if (contador.cuenta <= maximo) {
            return Resultado.PERMITIDO;
        }
        return new Resultado(true, Math.max(1, (contador.vence - ahora + 999) / 1000));
    }

    private void avisarRespaldo(DataAccessException e) {
        long ahora = reloj.getAsLong();
        // Atómico: solo el hilo que mueve la marca de tiempo escribe el aviso.
        long anterior = ultimoAviso.getAndAccumulate(ahora,
                (previo, nuevo) -> nuevo - previo >= AVISO_CADA_MS ? nuevo : previo);
        if (ahora - anterior >= AVISO_CADA_MS) {
            log.warn("Redis no disponible; los límites de solicitudes se cuentan en memoria de esta instancia ({})",
                    e.getClass().getSimpleName());
        }
    }
}
