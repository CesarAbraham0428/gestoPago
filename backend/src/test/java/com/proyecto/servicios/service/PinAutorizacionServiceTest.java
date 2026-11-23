package com.proyecto.servicios.service;

import com.proyecto.servicios.entity.sf.Cuenta;
import com.proyecto.servicios.service.exception.NegocioException;
import com.proyecto.servicios.service.exception.PinIncorrectoException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.junit.jupiter.api.Assertions.*;

class PinAutorizacionServiceTest {

    private static final Instant AHORA = Instant.parse("2026-10-28T12:00:00Z");
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(4);
    private final PinAutorizacionService service =
            new PinAutorizacionService(encoder, Clock.fixed(AHORA, ZoneOffset.UTC));
    private Cuenta cuenta;

    @BeforeEach
    void preparar() {
        cuenta = new Cuenta();
        cuenta.setPinHash(encoder.encode("1234"));
    }

    @Test
    void pinCorrectoReiniciaLosIntentos() {
        cuenta.setIntentosPinFallidos(3);

        service.verificar(cuenta, "1234");

        assertEquals(0, cuenta.getIntentosPinFallidos());
    }

    @Test
    void pinIncorrectoSumaUnIntento() {
        assertThrows(PinIncorrectoException.class, () -> service.verificar(cuenta, "9999"));
        assertEquals(1, cuenta.getIntentosPinFallidos());
        assertNull(cuenta.getPinBloqueadoHasta());
    }

    @Test
    void elQuintoIntentoFallidoBloqueaElPin() {
        cuenta.setIntentosPinFallidos(PinAutorizacionService.MAX_INTENTOS - 1);

        assertThrows(PinIncorrectoException.class, () -> service.verificar(cuenta, "9999"));

        assertEquals(AHORA.plus(PinAutorizacionService.DURACION_BLOQUEO), cuenta.getPinBloqueadoHasta());
        assertEquals(0, cuenta.getIntentosPinFallidos());
    }

    @Test
    void pinBloqueadoRechazaInclusoElPinCorrecto() {
        cuenta.setPinBloqueadoHasta(AHORA.plusSeconds(1));

        NegocioException error = assertThrows(NegocioException.class, () -> service.verificar(cuenta, "1234"));

        assertEquals(HttpStatus.LOCKED, error.getStatus());
    }

    @Test
    void alVencerElBloqueoSePuedeVolverAUsar() {
        cuenta.setPinBloqueadoHasta(AHORA);

        service.verificar(cuenta, "1234");

        assertNull(cuenta.getPinBloqueadoHasta());
    }
}
