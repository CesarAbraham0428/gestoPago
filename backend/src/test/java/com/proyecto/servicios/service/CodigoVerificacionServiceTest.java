package com.proyecto.servicios.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.proyecto.servicios.entity.sf.DesafioAutenticacion;
import com.proyecto.servicios.entity.sf.Usuario;
import com.proyecto.servicios.repositorys.sf.DesafioAutenticacionRepository;
import com.proyecto.servicios.repositorys.sf.UsuarioRepository;
import com.proyecto.servicios.service.CodigoVerificacionService.Verificado;
import com.proyecto.servicios.service.exception.NegocioException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;

class CodigoVerificacionServiceTest {

    private static final Instant AHORA = Instant.parse("2026-01-01T12:00:00Z");

    private final DesafioAutenticacionRepository desafios = mock(DesafioAutenticacionRepository.class);
    private final UsuarioRepository usuarios = mock(UsuarioRepository.class);
    private final Clock reloj = Clock.fixed(AHORA, ZoneOffset.UTC);
    private final TotpService totp = new TotpService(reloj);
    private CodigoVerificacionService service;
    private Usuario usuario;
    private String secreto;
    private String id;
    private DesafioAutenticacion desafio;

    @BeforeEach
    void preparar() {
        service = servicioA(AHORA);
        secreto = totp.nuevoSecreto();
        usuario = new Usuario();
        usuario.setId(7);
        usuario.setTotpSecreto(secreto);
        when(usuarios.findById(7)).thenReturn(Optional.of(usuario));
        when(desafios.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));
        id = service.emitir(7).id();
        ArgumentCaptor<DesafioAutenticacion> guardado = ArgumentCaptor.forClass(DesafioAutenticacion.class);
        verify(desafios).saveAndFlush(guardado.capture());
        desafio = guardado.getValue();
        when(desafios.bloquearPorId(id)).thenReturn(Optional.of(desafio));
    }

    private CodigoVerificacionService servicioA(Instant instante) {
        return new CodigoVerificacionService(desafios, usuarios, totp, Clock.fixed(instante, ZoneOffset.UTC),
                Duration.ofMinutes(5), 3);
    }

    private String codigoActual() {
        return totp.codigo(secreto, totp.pasoActual());
    }

    @Test
    void emiteUnDesafioQueVenceYReemplazaAlAnterior() {
        assertEquals(43, id.length());
        assertEquals(7, desafio.getUsuarioId());
        assertEquals(AHORA.plus(Duration.ofMinutes(5)), desafio.getExpiraEn());
        assertFalse(desafio.isVerificado());
        verify(desafios).eliminarDeUsuario(7);
    }

    @Test
    void elConstructorPublicoConvierteLosMinutos() {
        var publico = new CodigoVerificacionService(desafios, usuarios, totp, 5, 3);
        assertEquals(Duration.ofMinutes(5), publico.emitir(7).vigencia());
    }

    @Test
    void codigoCorrectoDevuelveAlUsuarioYElPasoSinConsumirElDesafio() {
        Verificado v = service.verificar(id, codigoActual());

        assertSame(usuario, v.usuario());
        assertEquals(totp.pasoActual(), v.paso());
        assertTrue(desafio.isVerificado());
        assertEquals(totp.pasoActual(), desafio.getPaso());
        verify(desafios, never()).delete(any());
        assertEquals(0, usuario.getTotpUltimoPaso()); // el paso se anota al abrir la sesión, no antes
    }

    @Test
    void reenviarTrasConfirmarElCierreDeOtraSesionNoPideOtroCodigo() {
        service.verificar(id, codigoActual());

        Verificado otra = service.verificar(id, "000000");

        assertEquals(totp.pasoActual(), otra.paso());
    }

    @Test
    void unCodigoYaUsadoPorElUsuarioSeRechaza() {
        usuario.setTotpUltimoPaso(totp.pasoActual());

        assertThrows(NegocioException.class, () -> service.verificar(id, codigoActual()));
        assertEquals(1, desafio.getIntentos());
    }

    @Test
    void codigoIncorrectoCuentaElIntentoYSeBloqueaAlLlegarAlMaximo() {
        String correcto = codigoActual();
        String incorrecto = correcto.equals("000000") ? "000001" : "000000";

        for (int i = 1; i <= 2; i++) {
            int intento = i;
            NegocioException e = assertThrows(NegocioException.class, () -> service.verificar(id, incorrecto));
            assertEquals(HttpStatus.UNAUTHORIZED, e.getStatus());
            assertEquals("Código de verificación incorrecto", e.getMessage());
            assertEquals(intento, desafio.getIntentos());
        }
        NegocioException ultimo = assertThrows(NegocioException.class, () -> service.verificar(id, incorrecto));

        assertEquals("Demasiados códigos incorrectos; inicia sesión de nuevo", ultimo.getMessage());
        verify(desafios).delete(desafio);
    }

    @Test
    void desafioVencidoOInexistenteSeRechazaConElMismoMensaje() {
        CodigoVerificacionService tarde = servicioA(AHORA.plus(Duration.ofMinutes(6)));

        NegocioException vencido = assertThrows(NegocioException.class, () -> tarde.verificar(id, codigoActual()));
        when(desafios.bloquearPorId("no-existe")).thenReturn(Optional.empty());
        NegocioException inexistente = assertThrows(NegocioException.class,
                () -> service.verificar("no-existe", "123456"));

        assertEquals(vencido.getMessage(), inexistente.getMessage());
        verify(desafios).delete(desafio);
    }

    @Test
    void siElUsuarioDelDesafioYaNoExisteSeRechaza() {
        when(usuarios.findById(7)).thenReturn(Optional.empty());

        assertThrows(NegocioException.class, () -> service.verificar(id, codigoActual()));
    }

    @Test
    void consumirEliminaElDesafio() {
        service.consumir(id);

        verify(desafios).deleteById(id);
    }
}
