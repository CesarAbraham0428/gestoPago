package com.proyecto.servicios.service;

import com.proyecto.servicios.entity.sf.Cuenta;
import com.proyecto.servicios.entity.sf.Usuario;
import com.proyecto.servicios.service.exception.NegocioException;
import com.proyecto.servicios.service.exception.OtpIncorrectoException;
import com.proyecto.servicios.service.exception.UsuarioNoEncontradoException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.OptionalLong;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * Autoriza un pago con el código TOTP de la app autenticadora del usuario que paga, el mismo mecanismo del
 * segundo factor del inicio de sesión. Tras varios códigos incorrectos se bloquean los pagos de la cuenta.
 * La cuenta debe llegar bloqueada (FOR UPDATE) y la transacción del llamador no debe revertirse con
 * {@link OtpIncorrectoException}, o el intento no contaría.
 */
@Service
public class OtpPagoService {

    static final int MAX_INTENTOS = 5;
    static final Duration DURACION_BLOQUEO = Duration.ofMinutes(15);

    private final TotpService totp;
    private final EntityManager em;
    private final Clock clock;

    @Autowired
    public OtpPagoService(TotpService totp, EntityManager em) {
        this(totp, em, Clock.systemUTC());
    }

    OtpPagoService(TotpService totp, EntityManager em, Clock clock) {
        this.totp = totp;
        this.em = em;
        this.clock = clock;
    }

    public void verificar(Cuenta cuenta, Integer usuarioId, String codigo) {
        Instant ahora = clock.instant();
        if (cuenta.getPinBloqueadoHasta() != null && ahora.isBefore(cuenta.getPinBloqueadoHasta())) {
            throw new NegocioException(HttpStatus.LOCKED,
                    "Los pagos están bloqueados por códigos incorrectos; intenta de nuevo más tarde");
        }
        Usuario usuario = em.find(Usuario.class, usuarioId);
        if (usuario == null) {
            throw new UsuarioNoEncontradoException();
        }
        // refresh + bloqueo: el usuario pudo cargarse antes y dos pagos simultáneos no deben aceptar el mismo código.
        em.refresh(usuario, LockModeType.PESSIMISTIC_WRITE);
        if (!usuario.isTotpConfirmado()) {
            throw new NegocioException(HttpStatus.CONFLICT,
                    "Configura tu app autenticadora antes de realizar pagos");
        }
        OptionalLong paso = totp.verificar(usuario.getTotpSecreto(), codigo, usuario.getTotpUltimoPaso());
        if (paso.isPresent()) {
            usuario.setTotpUltimoPaso(paso.getAsLong());
            cuenta.setIntentosPinFallidos(0);
            cuenta.setPinBloqueadoHasta(null);
            return;
        }
        int intentos = cuenta.getIntentosPinFallidos() + 1;
        if (intentos >= MAX_INTENTOS) {
            cuenta.setIntentosPinFallidos(0);
            cuenta.setPinBloqueadoHasta(ahora.plus(DURACION_BLOQUEO));
            throw new OtpIncorrectoException("Código incorrecto; los pagos se bloquearon por "
                    + DURACION_BLOQUEO.toMinutes() + " minutos por intentos fallidos");
        }
        cuenta.setIntentosPinFallidos(intentos);
        throw new OtpIncorrectoException(
                "Código incorrecto o ya usado; escribe el siguiente código de tu app autenticadora");
    }
}
