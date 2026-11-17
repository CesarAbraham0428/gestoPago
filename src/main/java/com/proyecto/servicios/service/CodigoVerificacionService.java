package com.proyecto.servicios.service;

import com.proyecto.servicios.entity.sf.DesafioAutenticacion;
import com.proyecto.servicios.repositorys.sf.DesafioAutenticacionRepository;
import com.proyecto.servicios.service.exception.NegocioException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Segundo factor: emite un código numérico de un solo uso, lo entrega por {@link EnviadorCodigo} y lo valida
 * en el servidor. El código se guarda con hash, vence pronto y admite pocos intentos; cada nuevo desafío
 * reemplaza al anterior y no se puede pedir otro de inmediato.
 *
 * Los métodos deben ejecutarse dentro de una transacción que NO se revierta con {@link NegocioException}:
 * un intento fallido tiene que quedar contado.
 */
@Service
@Slf4j
public class CodigoVerificacionService {

    public record Desafio(String id, Duration vigencia) {}

    private static final SecureRandom ALEATORIO = new SecureRandom();

    private final DesafioAutenticacionRepository desafios;
    private final EnviadorCodigo enviador;
    private final Clock clock;
    private final Duration vigencia;
    private final Duration espera;
    private final int maximoIntentos;

    @Autowired
    public CodigoVerificacionService(DesafioAutenticacionRepository desafios, EnviadorCodigo enviador,
            @Value("${gestopago.app.2fa.vigencia-minutos:5}") long vigenciaMinutos,
            @Value("${gestopago.app.2fa.espera-segundos:30}") long esperaSegundos,
            @Value("${gestopago.app.2fa.max-intentos:5}") int maximoIntentos) {
        this(desafios, enviador, Clock.systemUTC(), Duration.ofMinutes(vigenciaMinutos),
                Duration.ofSeconds(esperaSegundos), maximoIntentos);
    }

    CodigoVerificacionService(DesafioAutenticacionRepository desafios, EnviadorCodigo enviador, Clock clock,
            Duration vigencia, Duration espera, int maximoIntentos) {
        this.desafios = desafios;
        this.enviador = enviador;
        this.clock = clock;
        this.vigencia = vigencia;
        this.espera = espera;
        this.maximoIntentos = maximoIntentos;
    }

    @Transactional
    public Desafio emitir(Integer usuarioId, String correo) {
        Instant ahora = clock.instant();
        desafios.findByUsuarioId(usuarioId)
                .filter(anterior -> ahora.isBefore(anterior.getCreadoEn().plus(espera)))
                .ifPresent(anterior -> {
                    throw new NegocioException(HttpStatus.TOO_MANY_REQUESTS,
                            "Ya te enviamos un código; espera unos segundos antes de pedir otro");
                });
        desafios.eliminarDeUsuario(usuarioId);

        String id = aleatorioUrl();
        String codigo = String.format("%06d", ALEATORIO.nextInt(1_000_000));
        DesafioAutenticacion desafio = new DesafioAutenticacion();
        desafio.setId(id);
        desafio.setUsuarioId(usuarioId);
        desafio.setCodigoHash(hash(id, codigo));
        desafio.setCreadoEn(ahora);
        desafio.setExpiraEn(ahora.plus(vigencia));
        desafios.saveAndFlush(desafio);

        try {
            enviador.enviar(correo, codigo, vigencia);
        } catch (RuntimeException e) {
            // Sin entrega no hay desafío: el rollback evita bloquear al usuario con la espera entre códigos.
            log.error("No se pudo enviar el código de verificación ({})", e.getClass().getSimpleName());
            throw new NegocioException(HttpStatus.SERVICE_UNAVAILABLE,
                    "No pudimos enviar el código de verificación; intenta de nuevo en unos minutos");
        }
        return new Desafio(id, vigencia);
    }

    /** Valida el código y devuelve el usuario del desafío. No lo consume: ver {@link #consumir(String)}. */
    @Transactional(noRollbackFor = NegocioException.class)
    public Integer verificar(String desafioId, String codigo) {
        DesafioAutenticacion desafio = desafios.bloquearPorId(desafioId).orElseThrow(this::codigoInvalido);
        if (!clock.instant().isBefore(desafio.getExpiraEn())) {
            desafios.delete(desafio);
            throw codigoInvalido();
        }
        if (!MessageDigest.isEqual(hash(desafioId, codigo).getBytes(StandardCharsets.UTF_8),
                desafio.getCodigoHash().getBytes(StandardCharsets.UTF_8))) {
            desafio.setIntentos(desafio.getIntentos() + 1);
            if (desafio.getIntentos() >= maximoIntentos) {
                desafios.delete(desafio);
                throw new NegocioException(HttpStatus.UNAUTHORIZED,
                        "Demasiados códigos incorrectos; inicia sesión de nuevo");
            }
            throw new NegocioException(HttpStatus.UNAUTHORIZED, "Código de verificación incorrecto");
        }
        return desafio.getUsuarioId();
    }

    @Transactional
    public void consumir(String desafioId) {
        desafios.deleteById(desafioId);
    }

    private NegocioException codigoInvalido() {
        return new NegocioException(HttpStatus.UNAUTHORIZED, "Código de verificación incorrecto o vencido");
    }

    private static String aleatorioUrl() {
        byte[] bytes = new byte[32];
        ALEATORIO.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String hash(String id, String codigo) {
        return DigestUtils.sha256Hex(id + ":" + codigo);
    }
}
