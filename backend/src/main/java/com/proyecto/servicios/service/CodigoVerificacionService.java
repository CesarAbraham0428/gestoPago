package com.proyecto.servicios.service;

import com.proyecto.servicios.entity.sf.DesafioAutenticacion;
import com.proyecto.servicios.entity.sf.Usuario;
import com.proyecto.servicios.repositorys.sf.DesafioAutenticacionRepository;
import com.proyecto.servicios.repositorys.sf.UsuarioRepository;
import com.proyecto.servicios.service.exception.NegocioException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.OptionalLong;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Segundo factor: tras validar la contraseña se emite un desafío y el usuario lo completa con el código TOTP de
 * su app autenticadora, que se valida en el servidor. El desafío vence pronto, admite pocos intentos y cada uno
 * nuevo reemplaza al anterior.
 *
 * Los métodos deben ejecutarse dentro de una transacción que NO se revierta con {@link NegocioException}:
 * un intento fallido tiene que quedar contado.
 */
@Service
public class CodigoVerificacionService {

    public record Desafio(String id, Duration vigencia) {}

    /** Código aceptado: el usuario y el paso TOTP que se anotará como usado cuando se abra la sesión. */
    public record Verificado(Usuario usuario, long paso) {}

    private static final SecureRandom ALEATORIO = new SecureRandom();

    private final DesafioAutenticacionRepository desafios;
    private final UsuarioRepository usuarios;
    private final TotpService totp;
    private final Clock clock;
    private final Duration vigencia;
    private final int maximoIntentos;

    @Autowired
    public CodigoVerificacionService(DesafioAutenticacionRepository desafios, UsuarioRepository usuarios,
            TotpService totp,
            @Value("${gestopago.app.2fa.vigencia-minutos:5}") long vigenciaMinutos,
            @Value("${gestopago.app.2fa.max-intentos:5}") int maximoIntentos) {
        this(desafios, usuarios, totp, Clock.systemUTC(), Duration.ofMinutes(vigenciaMinutos), maximoIntentos);
    }

    CodigoVerificacionService(DesafioAutenticacionRepository desafios, UsuarioRepository usuarios, TotpService totp,
            Clock clock, Duration vigencia, int maximoIntentos) {
        this.desafios = desafios;
        this.usuarios = usuarios;
        this.totp = totp;
        this.clock = clock;
        this.vigencia = vigencia;
        this.maximoIntentos = maximoIntentos;
    }

    @Transactional
    public Desafio emitir(Integer usuarioId) {
        Instant ahora = clock.instant();
        desafios.eliminarDeUsuario(usuarioId);

        DesafioAutenticacion desafio = new DesafioAutenticacion();
        desafio.setId(aleatorioUrl());
        desafio.setUsuarioId(usuarioId);
        desafio.setCreadoEn(ahora);
        desafio.setExpiraEn(ahora.plus(vigencia));
        desafios.saveAndFlush(desafio);
        return new Desafio(desafio.getId(), vigencia);
    }

    /**
     * Valida el código TOTP. No lo consume ni anota el paso como usado: ver {@link #consumir(String)}. Si el código
     * ya fue aceptado antes (el cliente reenvía tras confirmar el cierre de otra sesión) no se vuelve a pedir.
     */
    @Transactional(noRollbackFor = NegocioException.class)
    public Verificado verificar(String desafioId, String codigo) {
        DesafioAutenticacion desafio = desafios.bloquearPorId(desafioId).orElseThrow(this::codigoInvalido);
        if (!clock.instant().isBefore(desafio.getExpiraEn())) {
            desafios.delete(desafio);
            throw codigoInvalido();
        }
        Usuario usuario = usuarios.findById(desafio.getUsuarioId()).orElseThrow(this::codigoInvalido);
        if (desafio.isVerificado()) {
            return new Verificado(usuario, desafio.getPaso());
        }
        OptionalLong paso = totp.verificar(usuario.getTotpSecreto(), codigo, usuario.getTotpUltimoPaso());
        if (paso.isEmpty()) {
            desafio.setIntentos(desafio.getIntentos() + 1);
            if (desafio.getIntentos() >= maximoIntentos) {
                desafios.delete(desafio);
                throw new NegocioException(HttpStatus.UNAUTHORIZED,
                        "Demasiados códigos incorrectos; inicia sesión de nuevo");
            }
            throw new NegocioException(HttpStatus.UNAUTHORIZED, "Código de verificación incorrecto");
        }
        desafio.setVerificado(true);
        desafio.setPaso(paso.getAsLong());
        return new Verificado(usuario, paso.getAsLong());
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
}
