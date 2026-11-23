package com.proyecto.servicios.service;

import com.proyecto.servicios.entity.sf.Cuenta;
import com.proyecto.servicios.service.exception.NegocioException;
import com.proyecto.servicios.service.exception.PinIncorrectoException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * Verifica el PIN de autorización y lo bloquea temporalmente tras varios intentos fallidos, para que un
 * PIN de 4 dígitos no se pueda adivinar por fuerza bruta. La cuenta debe llegar bloqueada (FOR UPDATE) y la
 * transacción del llamador no debe revertirse con {@link PinIncorrectoException}, o el intento no contaría.
 */
@Service
public class PinAutorizacionService {

    static final int MAX_INTENTOS = 5;
    static final Duration DURACION_BLOQUEO = Duration.ofMinutes(15);

    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    @Autowired
    public PinAutorizacionService(PasswordEncoder passwordEncoder) {
        this(passwordEncoder, Clock.systemUTC());
    }

    PinAutorizacionService(PasswordEncoder passwordEncoder, Clock clock) {
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
    }

    public void verificar(Cuenta cuenta, String pin) {
        Instant ahora = clock.instant();
        if (cuenta.getPinBloqueadoHasta() != null && ahora.isBefore(cuenta.getPinBloqueadoHasta())) {
            throw new NegocioException(HttpStatus.LOCKED,
                    "El PIN está bloqueado por intentos fallidos; intenta de nuevo más tarde");
        }
        if (pin != null && passwordEncoder.matches(pin, cuenta.getPinHash())) {
            cuenta.setIntentosPinFallidos(0);
            cuenta.setPinBloqueadoHasta(null);
            return;
        }
        int intentos = cuenta.getIntentosPinFallidos() + 1;
        if (intentos >= MAX_INTENTOS) {
            cuenta.setIntentosPinFallidos(0);
            cuenta.setPinBloqueadoHasta(ahora.plus(DURACION_BLOQUEO));
            throw new PinIncorrectoException("PIN incorrecto; se bloqueó por "
                    + DURACION_BLOQUEO.toMinutes() + " minutos por intentos fallidos");
        }
        cuenta.setIntentosPinFallidos(intentos);
        throw new PinIncorrectoException("PIN de autorización incorrecto");
    }
}
