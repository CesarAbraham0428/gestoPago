package com.proyecto.servicios.service;

import com.proyecto.servicios.entity.sf.Cuenta;
import com.proyecto.servicios.entity.sf.Usuario;
import com.proyecto.servicios.service.exception.NegocioException;
import com.proyecto.servicios.service.exception.OtpIncorrectoException;
import com.proyecto.servicios.service.exception.UsuarioNoEncontradoException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.OptionalLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OtpPagoServiceTest {

    private static final Instant AHORA = Instant.parse("2026-10-28T12:00:00Z");
    private final EntityManager em = mock(EntityManager.class);
    private final TotpService totp = new TotpService(Clock.fixed(AHORA, ZoneOffset.UTC));
    private final OtpPagoService service = new OtpPagoService(totp, em, Clock.fixed(AHORA, ZoneOffset.UTC));
    private Cuenta cuenta;
    private Usuario usuario;
    private String secreto;

    @BeforeEach
    void preparar() {
        cuenta = new Cuenta();
        secreto = totp.nuevoSecreto();
        usuario = new Usuario();
        usuario.setTotpSecreto(secreto);
        usuario.setTotpConfirmado(true);
        when(em.find(Usuario.class, 7)).thenReturn(usuario);
    }

    private String codigoActual() {
        return totp.codigo(secreto, totp.pasoActual());
    }

    @Test
    void codigoCorrectoReiniciaLosIntentosYLoMarcaComoUsado() {
        cuenta.setIntentosPinFallidos(3);

        service.verificar(cuenta, 7, codigoActual());

        assertEquals(0, cuenta.getIntentosPinFallidos());
        assertEquals(totp.pasoActual(), usuario.getTotpUltimoPaso());
        verify(em).refresh(usuario, LockModeType.PESSIMISTIC_WRITE);
    }

    @Test
    void unCodigoYaUsadoNoSirveDosVeces() {
        String codigo = codigoActual();
        service.verificar(cuenta, 7, codigo);

        assertThrows(OtpIncorrectoException.class, () -> service.verificar(cuenta, 7, codigo));
        assertEquals(1, cuenta.getIntentosPinFallidos());
    }

    @Test
    void codigoIncorrectoONuloSumaUnIntento() {
        assertThrows(OtpIncorrectoException.class, () -> service.verificar(cuenta, 7, "000000".equals(codigoActual()) ? "111111" : "000000"));
        assertThrows(OtpIncorrectoException.class, () -> service.verificar(cuenta, 7, null));
        assertEquals(2, cuenta.getIntentosPinFallidos());
        assertNull(cuenta.getPinBloqueadoHasta());
    }

    @Test
    void elQuintoIntentoFallidoBloqueaLosPagos() {
        cuenta.setIntentosPinFallidos(OtpPagoService.MAX_INTENTOS - 1);

        assertThrows(OtpIncorrectoException.class, () -> service.verificar(cuenta, 7, null));

        assertEquals(AHORA.plus(OtpPagoService.DURACION_BLOQUEO), cuenta.getPinBloqueadoHasta());
        assertEquals(0, cuenta.getIntentosPinFallidos());
    }

    @Test
    void cuentaBloqueadaRechazaInclusoElCodigoCorrecto() {
        cuenta.setPinBloqueadoHasta(AHORA.plusSeconds(1));

        NegocioException error = assertThrows(NegocioException.class,
                () -> service.verificar(cuenta, 7, codigoActual()));

        assertEquals(HttpStatus.LOCKED, error.getStatus());
    }

    @Test
    void alVencerElBloqueoSePuedeVolverAPagar() {
        cuenta.setPinBloqueadoHasta(AHORA);

        service.verificar(cuenta, 7, codigoActual());

        assertNull(cuenta.getPinBloqueadoHasta());
    }

    @Test
    void sinAppAutenticadoraConfirmadaNoSePuedePagar() {
        usuario.setTotpConfirmado(false);

        NegocioException error = assertThrows(NegocioException.class,
                () -> service.verificar(cuenta, 7, codigoActual()));

        assertEquals(HttpStatus.CONFLICT, error.getStatus());
        assertEquals(0, cuenta.getIntentosPinFallidos());
    }

    @Test
    void usuarioInexistente() {
        assertThrows(UsuarioNoEncontradoException.class, () -> service.verificar(cuenta, 99, "123456"));
    }

    @Test
    void elConstructorPublicoUsaElRelojDelSistema() {
        TotpService real = mock(TotpService.class);
        when(real.verificar(secreto, "123456", 0)).thenReturn(OptionalLong.of(5));

        new OtpPagoService(real, em).verificar(cuenta, 7, "123456");

        assertEquals(5, usuario.getTotpUltimoPaso());
    }
}
