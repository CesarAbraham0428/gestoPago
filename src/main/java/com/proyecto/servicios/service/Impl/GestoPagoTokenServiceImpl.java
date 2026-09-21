package com.proyecto.servicios.service.Impl;

import com.proyecto.servicios.client.GestoPagoAuthException;
import com.proyecto.servicios.client.GestoPagoAuthClient;
import com.proyecto.servicios.entity.gestopago.GestoPagoToken;
import com.proyecto.servicios.mapper.GestoPagoTokenMapper;
import com.proyecto.servicios.model.gestopago.GestoPagoAuthResponse;
import com.proyecto.servicios.repositorys.gestopago.GestoPagoTokenRepository;
import com.proyecto.servicios.service.GestoPagoTokenService;
import feign.FeignException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionException;

import java.net.SocketTimeoutException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Optional;

@Service
@Slf4j
public class GestoPagoTokenServiceImpl implements GestoPagoTokenService {

    private static final Duration REFRESH_SKEW = Duration.ofMinutes(1);

    private final GestoPagoAuthClient gestoPagoAuthClient;
    private final GestoPagoTokenRepository tokenRepository;
    private final GestoPagoTokenMapper tokenMapper;
    private final Integer idDistribuidor;
    private final String codigoDispositivo;
    private final String password;
    private final long refreshRateMs;

    public GestoPagoTokenServiceImpl(
            GestoPagoAuthClient gestoPagoAuthClient,
            GestoPagoTokenRepository tokenRepository,
            GestoPagoTokenMapper tokenMapper,
            @Value("${gestopago.auth.id-distribuidor}") Integer idDistribuidor,
            @Value("${gestopago.auth.codigo-dispositivo}") String codigoDispositivo,
            @Value("${gestopago.auth.password}") String password,
            @Value("${gestopago.auth.refresh-rate-ms:3600000}") long refreshRateMs) {
        this.gestoPagoAuthClient = gestoPagoAuthClient;
        this.tokenRepository = tokenRepository;
        this.tokenMapper = tokenMapper;
        this.idDistribuidor = idDistribuidor;
        this.codigoDispositivo = codigoDispositivo;
        this.password = password;
        this.refreshRateMs = refreshRateMs;
    }

    @Override
    public synchronized GestoPagoToken renovarToken() {
        return renovarTokenInterno();
    }

    @Scheduled(fixedDelayString = "${gestopago.auth.renewal-check-ms:60000}", initialDelay = 0)
    public synchronized void revisarRenovacionProgramada() {
        long startedAt = System.nanoTime();
        log.info("Inicia revisión programada del token de GestoPago");
        try {
            Optional<GestoPagoToken> actual = tokenRepository
                    .findByIdDistribuidorAndCodigoDispositivoAndActivoTrue(idDistribuidor, codigoDispositivo);
            if (actual.isPresent() && !necesitaRenovacion(actual.get(), LocalDateTime.now())) {
                log.debug("El token de GestoPago todavía está vigente; no se renueva");
                return;
            }
            renovarTokenInterno();
        } catch (GestoPagoAuthException exception) {
            log.error("No se pudo renovar el token de GestoPago: tipo={}, statusHttp={}",
                    exception.getTipo(), exception.getStatusHttp());
        } catch (DataAccessException | TransactionException exception) {
            log.error("Falló el acceso a PostgreSQL durante la renovación del token ({})",
                    exception.getClass().getSimpleName());
        } finally {
            log.info("Finaliza revisión programada del token de GestoPago: duraciónMs={}", duracionMs(startedAt));
        }
    }

    @Override
    public synchronized Optional<GestoPagoToken> obtenerTokenActivo(
            Integer idDistribuidor, String codigoDispositivo) {
        Optional<GestoPagoToken> token = tokenRepository
                .findByIdDistribuidorAndCodigoDispositivoAndActivoTrue(idDistribuidor, codigoDispositivo);

        if (token.isPresent() && !necesitaRenovacion(token.get(), LocalDateTime.now())) {
            return token;
        }

        try {
            return Optional.of(renovarTokenInterno());
        } catch (GestoPagoAuthException exception) {
            if (token.isPresent() && sigueVigente(token.get(), LocalDateTime.now())) {
                log.warn("Falló la renovación anticipada; se usará el token vigente. tipo={}, statusHttp={}",
                        exception.getTipo(), exception.getStatusHttp());
                return token;
            }
            throw exception;
        }
    }

    private GestoPagoToken renovarTokenInterno() {
        long startedAt = System.nanoTime();
        log.info("Inicia solicitud de renovación del token de GestoPago para distribuidor={}", idDistribuidor);
        try {
            GestoPagoAuthResponse response = gestoPagoAuthClient.authenticate(
                    idDistribuidor, codigoDispositivo, password);
            if (response == null || response.getToken() == null || response.getToken().isBlank()
                    || (response.getExpiresIn() != null && response.getExpiresIn() <= 0)) {
                throw new GestoPagoAuthException(
                        "GestoPago no devolvió un token válido",
                        GestoPagoAuthException.Tipo.RESPUESTA_INVALIDA,
                        null,
                        null);
            }

            GestoPagoToken tokenEntity = tokenRepository
                    .findByIdDistribuidorAndCodigoDispositivo(idDistribuidor, codigoDispositivo)
                    .map(existing -> {
                        tokenMapper.updateEntity(response, existing);
                        return existing;
                    })
                    .orElseGet(() -> {
                        GestoPagoToken nuevo = tokenMapper.toEntity(response);
                        nuevo.setIdDistribuidor(idDistribuidor);
                        nuevo.setCodigoDispositivo(codigoDispositivo);
                        return nuevo;
                    });

            tokenEntity.setActivo(true);
            GestoPagoToken persistido = tokenRepository.saveAndFlush(tokenEntity);
            log.info("Token de GestoPago renovado y persistido; expiresInSegundos={}", persistido.getExpiresIn());
            return persistido;
        } catch (FeignException exception) {
            GestoPagoAuthException translated = traducirErrorAutenticacion(exception);
            log.error("Falló la autenticación de GestoPago: tipo={}, statusHttp={}, duraciónMs={}",
                    translated.getTipo(), translated.getStatusHttp(), duracionMs(startedAt));
            throw translated;
        } catch (DataAccessException | TransactionException exception) {
            log.error("Falló PostgreSQL al persistir el token de GestoPago ({})",
                    exception.getClass().getSimpleName());
            throw exception;
        } catch (GestoPagoAuthException exception) {
            log.error("GestoPago devolvió una respuesta de autenticación inválida: tipo={}", exception.getTipo());
            throw exception;
        } finally {
            log.info("Finaliza solicitud de renovación del token de GestoPago: duraciónMs={}", duracionMs(startedAt));
        }
    }

    private GestoPagoAuthException traducirErrorAutenticacion(FeignException exception) {
        Integer status = exception.status() >= 0 ? exception.status() : null;
        GestoPagoAuthException.Tipo tipo;
        String mensaje;
        if (status != null && (status == 401 || status == 403)) {
            tipo = GestoPagoAuthException.Tipo.AUTENTICACION;
            mensaje = "GestoPago rechazó las credenciales de autenticación";
        } else if (status != null) {
            tipo = GestoPagoAuthException.Tipo.HTTP;
            mensaje = "GestoPago devolvió un estado HTTP no exitoso al autenticar";
        } else if (contieneTimeout(exception)) {
            tipo = GestoPagoAuthException.Tipo.TIMEOUT;
            mensaje = "Se agotó el tiempo de espera al autenticar con GestoPago";
        } else {
            tipo = GestoPagoAuthException.Tipo.COMUNICACION;
            mensaje = "No se pudo establecer comunicación con el servicio de autenticación de GestoPago";
        }
        return new GestoPagoAuthException(mensaje, tipo, status, exception);
    }

    private boolean necesitaRenovacion(GestoPagoToken token, LocalDateTime ahora) {
        if (token == null || token.getToken() == null || token.getToken().isBlank()) {
            return true;
        }
        LocalDateTime vence = fechaEmision(token).plus(duracionToken(token));
        Duration margen = duracionToken(token).compareTo(REFRESH_SKEW) > 0
                ? REFRESH_SKEW
                : duracionToken(token).dividedBy(10);
        return !vence.isAfter(ahora.plus(margen));
    }

    private boolean sigueVigente(GestoPagoToken token, LocalDateTime ahora) {
        return token.getToken() != null
                && !token.getToken().isBlank()
                && fechaEmision(token).plus(duracionToken(token)).isAfter(ahora);
    }

    private LocalDateTime fechaEmision(GestoPagoToken token) {
        if (token.getFechaActualizacion() != null) {
            return token.getFechaActualizacion();
        }
        if (token.getFechaCreacion() != null) {
            return token.getFechaCreacion();
        }
        return LocalDateTime.MIN;
    }

    private Duration duracionToken(GestoPagoToken token) {
        if (token.getExpiresIn() != null && token.getExpiresIn() > 0) {
            return Duration.ofSeconds(token.getExpiresIn());
        }
        return Duration.ofMillis(Math.max(refreshRateMs, 1));
    }

    private boolean contieneTimeout(Throwable exception) {
        Throwable actual = exception;
        while (actual != null) {
            if (actual instanceof SocketTimeoutException
                    || actual.getClass().getSimpleName().toLowerCase(Locale.ROOT).contains("timeout")) {
                return true;
            }
            String message = actual.getMessage();
            if (message != null) {
                String normalized = message.toLowerCase(Locale.ROOT);
                if (normalized.contains("timed out") || normalized.contains("timeout")) {
                    return true;
                }
            }
            actual = actual.getCause();
        }
        return false;
    }

    private long duracionMs(long startedAt) {
        return Duration.ofNanos(System.nanoTime() - startedAt).toMillis();
    }
}
