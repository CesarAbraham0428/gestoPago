package com.proyecto.servicios.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.proyecto.servicios.entity.sf.DesafioAutenticacion;
import com.proyecto.servicios.repositorys.sf.DesafioAutenticacionRepository;
import com.proyecto.servicios.service.CodigoVerificacionService.Desafio;
import com.proyecto.servicios.service.exception.NegocioException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;

class CodigoVerificacionServiceUnitTest {

    private static final Instant AHORA = Instant.parse("2026-11-01T10:00:00Z");
    private final DesafioAutenticacionRepository desafios = mock(DesafioAutenticacionRepository.class);
    private final EnviadorCodigo enviador = mock(EnviadorCodigo.class);
    private final CodigoVerificacionService servicio = new CodigoVerificacionService(desafios, enviador,
            Clock.fixed(AHORA, ZoneOffset.UTC), Duration.ofMinutes(5), Duration.ofSeconds(30), 3);

    /** Emite un desafío y devuelve el código que se habría enviado por correo. */
    private String emitirYCapturarCodigo(DesafioAutenticacion[] guardado, Desafio[] desafio) {
        when(desafios.findByUsuarioId(1)).thenReturn(Optional.empty());
        desafio[0] = servicio.emitir(1, "ana@correo.mx");
        ArgumentCaptor<DesafioAutenticacion> entidad = ArgumentCaptor.forClass(DesafioAutenticacion.class);
        verify(desafios).saveAndFlush(entidad.capture());
        guardado[0] = entidad.getValue();
        ArgumentCaptor<String> codigo = ArgumentCaptor.forClass(String.class);
        verify(enviador).enviar(anyString(), codigo.capture(), any(Duration.class));
        return codigo.getValue();
    }

    @Test
    void emitirGuardaElHashYEnviaElCodigoDeSeisDigitos() {
        DesafioAutenticacion[] guardado = new DesafioAutenticacion[1];
        Desafio[] desafio = new Desafio[1];

        String codigo = emitirYCapturarCodigo(guardado, desafio);

        assertEquals(6, codigo.length());
        assertEquals(desafio[0].id(), guardado[0].getId());
        assertEquals(Duration.ofMinutes(5), desafio[0].vigencia());
        assertEquals(AHORA.plus(Duration.ofMinutes(5)), guardado[0].getExpiraEn());
        assertNotNull(guardado[0].getCodigoHash());
        verify(desafios).eliminarDeUsuario(1);
    }

    @Test
    void noPermitePedirOtroCodigoDeInmediato() {
        DesafioAutenticacion anterior = new DesafioAutenticacion();
        anterior.setCreadoEn(AHORA.minusSeconds(10));
        when(desafios.findByUsuarioId(1)).thenReturn(Optional.of(anterior));

        NegocioException error = assertThrows(NegocioException.class, () -> servicio.emitir(1, "a@b.mx"));

        assertEquals(HttpStatus.TOO_MANY_REQUESTS, error.getStatus());
        verify(desafios, never()).saveAndFlush(any());
    }

    @Test
    void pasadaLaEsperaSeReemplazaElDesafioAnterior() {
        DesafioAutenticacion anterior = new DesafioAutenticacion();
        anterior.setCreadoEn(AHORA.minusSeconds(31));
        when(desafios.findByUsuarioId(1)).thenReturn(Optional.of(anterior));

        servicio.emitir(1, "a@b.mx");

        verify(desafios).eliminarDeUsuario(1);
    }

    @Test
    void siNoSePuedeEnviarElCodigoResponde503() {
        when(desafios.findByUsuarioId(1)).thenReturn(Optional.empty());
        doThrow(new IllegalStateException("smtp")).when(enviador).enviar(anyString(), anyString(), any());

        NegocioException error = assertThrows(NegocioException.class, () -> servicio.emitir(1, "a@b.mx"));

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, error.getStatus());
    }

    @Test
    void verificarConElCodigoCorrectoDevuelveAlUsuario() {
        DesafioAutenticacion[] guardado = new DesafioAutenticacion[1];
        String codigo = emitirYCapturarCodigo(guardado, new Desafio[1]);
        when(desafios.bloquearPorId(guardado[0].getId())).thenReturn(Optional.of(guardado[0]));

        assertEquals(1, servicio.verificar(guardado[0].getId(), codigo));
    }

    @Test
    void verificarConUnCodigoIncorrectoCuentaElIntentoYAlLlegarAlMaximoElimina() {
        DesafioAutenticacion[] guardado = new DesafioAutenticacion[1];
        String codigo = emitirYCapturarCodigo(guardado, new Desafio[1]);
        String incorrecto = codigo.equals("000000") ? "000001" : "000000";
        String id = guardado[0].getId();
        when(desafios.bloquearPorId(id)).thenReturn(Optional.of(guardado[0]));

        for (int intento = 1; intento <= 2; intento++) {
            NegocioException error = assertThrows(NegocioException.class, () -> servicio.verificar(id, incorrecto));
            assertEquals("Código de verificación incorrecto", error.getMessage());
        }
        verify(desafios, never()).delete(any());

        NegocioException ultimo = assertThrows(NegocioException.class, () -> servicio.verificar(id, incorrecto));
        assertEquals("Demasiados códigos incorrectos; inicia sesión de nuevo", ultimo.getMessage());
        verify(desafios).delete(guardado[0]);
    }

    @Test
    void verificarUnDesafioVencidoOInexistenteSeRechaza() {
        DesafioAutenticacion vencido = new DesafioAutenticacion();
        vencido.setExpiraEn(AHORA);
        when(desafios.bloquearPorId("v")).thenReturn(Optional.of(vencido));
        when(desafios.bloquearPorId("x")).thenReturn(Optional.empty());

        assertThrows(NegocioException.class, () -> servicio.verificar("v", "123456"));
        verify(desafios).delete(vencido);
        assertThrows(NegocioException.class, () -> servicio.verificar("x", "123456"));
    }

    @Test
    void consumirEliminaElDesafio() {
        servicio.consumir("id");

        verify(desafios).deleteById("id");
    }

    @Test
    void elConstructorPublicoConvierteLasUnidades() {
        CodigoVerificacionService publico = new CodigoVerificacionService(desafios, enviador, 5, 30, 3);
        when(desafios.findByUsuarioId(2)).thenReturn(Optional.empty());

        assertEquals(Duration.ofMinutes(5), publico.emitir(2, "a@b.mx").vigencia());
        Matcher id = Pattern.compile("[A-Za-z0-9_-]{43}").matcher(captarId());
        assertEquals(true, id.matches());
    }

    private String captarId() {
        ArgumentCaptor<DesafioAutenticacion> entidad = ArgumentCaptor.forClass(DesafioAutenticacion.class);
        verify(desafios).saveAndFlush(entidad.capture());
        return entidad.getValue().getId();
    }
}
