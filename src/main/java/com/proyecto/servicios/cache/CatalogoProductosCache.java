package com.proyecto.servicios.cache;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.proyecto.servicios.model.CatalogoProductosResponse;
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
        final String json;
        try {
            json = redisTemplate.opsForValue().get(CACHE_KEY);
        } catch (DataAccessException exception) {
            log.warn("No se pudo consultar Redis; se continuará con PostgreSQL ({})",
                    exception.getClass().getSimpleName());
            return new ResultadoLectura(Optional.empty(), false);
        }

        if (json == null) {
            return new ResultadoLectura(Optional.empty(), true);
        }
        if (json.isBlank()) {
            return new ResultadoLectura(Optional.empty(), eliminar());
        }

        try {
            CatalogoProductosResponse catalogo = objectMapper.readValue(json, CatalogoProductosResponse.class);
            if (catalogo == null || catalogo.getProductos() == null || catalogo.getProductos().isEmpty()) {
                log.warn("Redis contiene un catálogo vacío; se volverá a consultar PostgreSQL");
                return new ResultadoLectura(Optional.empty(), eliminar());
            }
            return new ResultadoLectura(Optional.of(catalogo), true);
        } catch (JsonProcessingException exception) {
            log.warn("Redis contiene un catálogo inválido; se volverá a consultar PostgreSQL ({})",
                    exception.getClass().getSimpleName());
            return new ResultadoLectura(Optional.empty(), eliminar());
        }
    }

    public boolean guardar(CatalogoProductosResponse catalogo) {
        if (catalogo == null || catalogo.getProductos() == null || catalogo.getProductos().isEmpty()) {
            return false;
        }

        try {
            String json = objectMapper.writeValueAsString(catalogo);
            redisTemplate.opsForValue().set(CACHE_KEY, json, ttl);
            return true;
        } catch (JsonProcessingException | DataAccessException exception) {
            log.warn("No se pudo guardar el catálogo en Redis; se conservará el respaldo de PostgreSQL ({})",
                    exception.getClass().getSimpleName());
            return false;
        }
    }

    private boolean eliminar() {
        try {
            redisTemplate.delete(CACHE_KEY);
            return true;
        } catch (DataAccessException exception) {
            log.warn("No se pudo eliminar el catálogo inválido de Redis ({})",
                    exception.getClass().getSimpleName());
            return false;
        }
    }

    public record ResultadoLectura(
            Optional<CatalogoProductosResponse> catalogo,
            boolean redisDisponible) {
    }
}
