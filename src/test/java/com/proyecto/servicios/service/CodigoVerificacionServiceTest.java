package com.proyecto.servicios.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.proyecto.servicios.entity.sf.DesafioAutenticacion;
import com.proyecto.servicios.repositorys.sf.DesafioAutenticacionRepository;
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
    private final EnviadorCodigo enviador = mock(EnviadorCodigo.class);
    private CodigoVerificacionService service;
    private String id;
    private String codigoEnviado;

    @BeforeEach
    void preparar() {
        service = servicioA(AHORA);
        when(desafios.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));
    }

    private CodigoVerificacionService servicioA(Instant instante) {
        return new CodigoVerificacionService(desafios, enviador, Clock.fixed(instante, ZoneOffset.UTC),
                Duration.ofMinutes(5), Duration.ofSeconds(30), 3);
    }

    /** Emite un desafío y deja guardado en {@code id} y {@code codigoEnviado} lo que recibiría el usuario. */
    private DesafioAutenticacion emitir() {
        var emitido = service.emitir(7, "ana@example.com");
        id = emitido.id();
        ArgumentCaptor<DesafioAutenticacion> guardado = ArgumentCaptor.forClass(DesafioAutenticacion.class);
        verify(desafios).saveAndFlush(guardado.capture());
        ArgumentCaptor<String> codigo = ArgumentCaptor.forClass(String.class);
        verify(enviador).enviar(eq("ana@example.com"), codigo.capture(), eq(Duration.ofMinutes(5)));
        codigoEnviado = codigo.getValue();
        when(desafios.bloquearPorId(id)).thenReturn(Optional.of(guardado.getValue()));
        return guardado.getValue();
    }

    @Test
    void emiteCodigoDeSeisDigitosYGuardaSoloSuHash() {
        DesafioAutenticacion desafio = emitir();

        assertTrue(codigoEnviado.matches("\\d{6}"));
        assertEquals(43, id.length());
        assertNotEquals(codigoEnviado, desafio.getCodigoHash());
        assertFalse(desafio.getCodigoHash().contains(codigoEnviado));
        assertEquals(AHORA.plus(Duration.ofMinutes(5)), desafio.getExpiraEn());
        verify(desafios).eliminarDeUsuario(7);
    }

    @Test
    void noPermitePedirOtroCodigoDeInmediato() {
        DesafioAutenticacion reciente = new DesafioAutenticacion();
        reciente.setCreadoEn(AHORA.minusSeconds(10));
        when(desafios.findByUsuarioId(7)).thenReturn(Optional.of(reciente));

        NegocioException e = assertThrows(NegocioException.class, () -> service.emitir(7, "ana@example.com"));

        assertEquals(HttpStatus.TOO_MANY_REQUESTS, e.getStatus());
        verifyNoInteractions(enviador);
    }

    @Test
    void siElCorreoFallaNoSeConservaElDesafioNiSeFiltraElMotivo() {
        doThrow(new IllegalStateException("smtp caído: credenciales")).when(enviador).enviar(any(), any(), any());

        NegocioException e = assertThrows(NegocioException.class, () -> service.emitir(7, "ana@example.com"));

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, e.getStatus());
        assertFalse(e.getMessage().contains("smtp"));
    }

    @Test
    void codigoCorrectoDevuelveElUsuarioSinConsumirElDesafio() {
        emitir();
        assertEquals(7, service.verificar(id, codigoEnviado));
        verify(desafios, never()).delete(any());
    }

    @Test
    void codigoIncorrectoCuentaElIntentoYSeBloqueaAlLlegarAlMaximo() {
        DesafioAutenticacion desafio = emitir();
        String incorrecto = codigoEnviado.equals("000000") ? "000001" : "000000";

        for (int i = 1; i <= 2; i++) {
            NegocioException e = assertThrows(NegocioException.class, () -> service.verificar(id, incorrecto));
            assertEquals(HttpStatus.UNAUTHORIZED, e.getStatus());
            assertEquals(i, desafio.getIntentos());
        }
        assertThrows(NegocioException.class, () -> service.verificar(id, incorrecto));

        verify(desafios).delete(desafio);
    }

    @Test
    void codigoVencidoOInexistenteSeRechazaConElMismoMensaje() {
        DesafioAutenticacion desafio = emitir();
        CodigoVerificacionService tarde = servicioA(AHORA.plus(Duration.ofMinutes(6)));

        NegocioException vencido = assertThrows(NegocioException.class, () -> tarde.verificar(id, codigoEnviado));
        when(desafios.bloquearPorId("no-existe")).thenReturn(Optional.empty());
        NegocioException inexistente = assertThrows(NegocioException.class, () -> service.verificar("no-existe", "123456"));

        assertEquals(vencido.getMessage(), inexistente.getMessage());
        verify(desafios).delete(desafio);
    }
}
