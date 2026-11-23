package com.proyecto.servicios.service.Impl;

import com.proyecto.servicios.client.GestoPagoAuthClient;
import com.proyecto.servicios.client.GestoPagoAuthException;
import com.proyecto.servicios.entity.gestopago.GestoPagoToken;
import com.proyecto.servicios.mapper.GestoPagoTokenMapper;
import com.proyecto.servicios.models.response.gestopago.GestoPagoAuthResponse;
import com.proyecto.servicios.repositorys.gestopago.GestoPagoTokenRepository;
import com.proyecto.servicios.service.GestoPagoTokenService;
import feign.FeignException;
import java.time.Clock;
import java.time.Duration;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionException;

/** Obtiene, renueva y persiste el token de GestoPago. La política de vigencia vive en {@link VigenciaToken}. */
@Service
@Slf4j
public class GestoPagoTokenServiceImpl implements GestoPagoTokenService {

    private final GestoPagoAuthClient gestoPagoAuthClient;
    private final GestoPagoTokenRepository tokenRepository;
    private final GestoPagoTokenMapper tokenMapper;
    private final Integer idDistribuidor;
    private final String codigoDispositivo;
    private final String password;
    private final VigenciaToken vigencia;

    @Autowired
    public GestoPagoTokenServiceImpl(
            GestoPagoAuthClient gestoPagoAuthClient,
            GestoPagoTokenRepository tokenRepository,
            GestoPagoTokenMapper tokenMapper,
            @Value("${gestopago.auth.id-distribuidor}") Integer idDistribuidor,
            @Value("${gestopago.auth.codigo-dispositivo}") String codigoDispositivo,
            @Value("${gestopago.auth.password}") String password,
            @Value("${gestopago.auth.refresh-rate-ms:3600000}") long refreshRateMs) {
        this(gestoPagoAuthClient, tokenRepository, tokenMapper, idDistribuidor, codigoDispositivo,
            password, refreshRateMs, Clock.systemDefaultZone());
    }

    GestoPagoTokenServiceImpl(GestoPagoAuthClient gestoPagoAuthClient, GestoPagoTokenRepository tokenRepository,
        GestoPagoTokenMapper tokenMapper, Integer idDistribuidor, String codigoDispositivo,
        String password, long refreshRateMs, Clock clock) {
        this.gestoPagoAuthClient = gestoPagoAuthClient;
        this.tokenRepository = tokenRepository;
        this.tokenMapper = tokenMapper;
        this.idDistribuidor = idDistribuidor;
        this.codigoDispositivo = codigoDispositivo;
        this.password = password;
        this.vigencia = new VigenciaToken(refreshRateMs, clock);
    }

    @Override
    public synchronized GestoPagoToken renovarToken() {
        return renovarTokenInterno();
    }

    @Override
    public synchronized void renovarSiHaceFalta() {
        boolean vigente = tokenRepository
                .findByIdDistribuidorAndCodigoDispositivoAndActivoTrue(idDistribuidor, codigoDispositivo)
                .filter(actual -> !vigencia.necesitaRenovacion(actual))
                .isPresent();
        if (vigente) {
            log.debug("El token de GestoPago todavía está vigente; no se renueva");
            return;
        }
        renovarTokenInterno();
    }

    @Override
    public synchronized Optional<GestoPagoToken> obtenerTokenActivo(
            Integer idDistribuidor, String codigoDispositivo) {
        Optional<GestoPagoToken> token = tokenRepository
                .findByIdDistribuidorAndCodigoDispositivoAndActivoTrue(idDistribuidor, codigoDispositivo);

        if (token.isPresent() && !vigencia.necesitaRenovacion(token.get())) {
            return token;
        }

        try {
            return Optional.of(renovarTokenInterno());
        } catch (GestoPagoAuthException exception) {
            if (token.isPresent() && vigencia.sigueVigente(token.get())) {
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
            if (!esValida(response)) {
                throw new GestoPagoAuthException("GestoPago no devolvió un token válido",
                        GestoPagoAuthException.Tipo.RESPUESTA_INVALIDA, null, null);
            }
            GestoPagoToken persistido = tokenRepository.saveAndFlush(conRespuesta(response));
            log.info("Token de GestoPago renovado y persistido; expiresInSegundos={}", persistido.getExpiresIn());
            return persistido;
        } catch (FeignException exception) {
            GestoPagoAuthException translated = GestoPagoAuthException.desde(exception);
            log.error("Falló la autenticación de GestoPago: tipo={}, statusHttp={}, duraciónMs={}",
                    translated.getTipo(), translated.getStatusHttp(), duracionMs(startedAt));
            throw translated;
        } finally {
            log.info("Finaliza solicitud de renovación del token de GestoPago: duraciónMs={}", duracionMs(startedAt));
        }
    }

    private boolean esValida(GestoPagoAuthResponse response) {
        return response != null && response.getToken() != null && !response.getToken().isBlank()
                && (response.getExpiresIn() == null || response.getExpiresIn() > 0);
    }

    /** Actualiza el registro existente del dispositivo o crea uno nuevo, siempre activo. */
    private GestoPagoToken conRespuesta(GestoPagoAuthResponse response) {
        GestoPagoToken token = tokenRepository
                .findByIdDistribuidorAndCodigoDispositivo(idDistribuidor, codigoDispositivo)
                .map(existente -> {
                    tokenMapper.updateEntity(response, existente);
                    return existente;
                })
                .orElseGet(() -> {
                    GestoPagoToken nuevo = tokenMapper.toEntity(response);
                    nuevo.setIdDistribuidor(idDistribuidor);
                    nuevo.setCodigoDispositivo(codigoDispositivo);
                    return nuevo;
                });
        token.setActivo(true);
        return token;
    }

    private long duracionMs(long startedAt) {
        return Duration.ofNanos(System.nanoTime() - startedAt).toMillis();
    }
}
