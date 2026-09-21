package com.proyecto.servicios.cache;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.proyecto.servicios.model.CatalogoProductosResponse;
import com.proyecto.servicios.validation.CatalogoProductosValidator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;

@Component
@Slf4j
public class CatalogoProductosCache {

    private static final String CACHE_KEY = "gestopago:catalogo:productos:v1";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final Duration ttl;

    public CatalogoProductosCache(
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            @Value("${gestopago.catalog.cache-ttl:24h}") Duration ttl) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.ttl = ttl;
    }

    public ResultadoLectura obtener() {
        long startedAt = System.nanoTime();
        log.info("Inicia lectura del catálogo en Redis");
        try {
            return obtenerInterno();
        } finally {
            log.info("Finaliza lectura del catálogo en Redis: duraciónMs={}", duracionMs(startedAt));
        }
    }

    private ResultadoLectura obtenerInterno() {
        final String json;
        try {
            json = redisTemplate.opsForValue().get(CACHE_KEY);
        } catch (DataAccessException exception) {
            log.error("Falló la consulta del catálogo en Redis; se continuará con PostgreSQL ({})",
                    exception.getClass().getSimpleName());
            return new ResultadoLectura(Optional.empty(), false);
        }

        if (json == null) {
            log.info("Redis no contiene un catálogo; se consultará PostgreSQL");
            return new ResultadoLectura(Optional.empty(), true);
        }
        if (json.isBlank()) {
            log.warn("Redis contiene un valor vacío; se eliminará antes del fallback");
            return new ResultadoLectura(Optional.empty(), eliminar());
        }

        try {
            CatalogoProductosResponse catalogo = objectMapper.readValue(json, CatalogoProductosResponse.class);
            if (!CatalogoProductosValidator.esValido(catalogo)) {
                log.warn("Redis contiene productos vacíos o incompletos; se consultará PostgreSQL");
                return new ResultadoLectura(Optional.empty(), eliminar());
            }
            log.info("Redis devolvió un catálogo válido: {} productos", catalogo.getProductos().size());
            return new ResultadoLectura(Optional.of(catalogo), true);
        } catch (JsonProcessingException exception) {
            log.warn("Redis contiene JSON inválido; se consultará PostgreSQL ({})",
                    exception.getClass().getSimpleName());
            return new ResultadoLectura(Optional.empty(), eliminar());
        }
    }

    public boolean guardar(CatalogoProductosResponse catalogo) {
        long startedAt = System.nanoTime();
        int cantidad = catalogo == null || catalogo.getProductos() == null ? 0 : catalogo.getProductos().size();
        log.info("Inicia escritura del catálogo en Redis: productos={}", cantidad);
        try {
            if (!CatalogoProductosValidator.esValido(catalogo)) {
                log.warn("Se rechazó un catálogo vacío o incompleto para escribir en Redis");
                return false;
            }

            String json = objectMapper.writeValueAsString(catalogo);
            redisTemplate.opsForValue().set(CACHE_KEY, json, ttl);
            log.info("Catálogo guardado en Redis: productos={}, ttl={}", cantidad, ttl);
            return true;
        } catch (JsonProcessingException exception) {
            log.error("No se pudo serializar el catálogo para Redis ({})", exception.getClass().getSimpleName());
            eliminarCatalogoAnteriorTrasFallo();
            return false;
        } catch (DataAccessException exception) {
            log.error("Falló la escritura del catálogo en Redis; PostgreSQL conserva el respaldo ({})",
                    exception.getClass().getSimpleName());
            eliminarCatalogoAnteriorTrasFallo();
            return false;
        } finally {
            log.info("Finaliza escritura del catálogo en Redis: duraciónMs={}", duracionMs(startedAt));
        }
    }

    private boolean eliminar() {
        try {
            Boolean eliminado = redisTemplate.delete(CACHE_KEY);
            if (!Boolean.TRUE.equals(eliminado)) {
                log.warn("Redis no confirmó la eliminación del catálogo cacheado");
                return false;
            }
            return true;
        } catch (DataAccessException exception) {
            log.warn("No se pudo eliminar el catálogo inválido de Redis ({})",
                    exception.getClass().getSimpleName());
            return false;
        }
    }

    private void eliminarCatalogoAnteriorTrasFallo() {
        if (!eliminar()) {
            log.warn("No fue posible invalidar el catálogo Redis anterior; podría permanecer hasta que venza su TTL");
        }
    }

    private long duracionMs(long startedAt) {
        return Duration.ofNanos(System.nanoTime() - startedAt).toMillis();
    }

    public record ResultadoLectura(
            Optional<CatalogoProductosResponse> catalogo,
            boolean redisDisponible) {
    }
}
