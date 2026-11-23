package com.proyecto.servicios.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.proyecto.servicios.entity.sf.Cliente;
import com.proyecto.servicios.entity.sf.Usuario;
import com.proyecto.servicios.models.request.LoginRequest;
import com.proyecto.servicios.models.request.VerificacionCodigoRequest;
import com.proyecto.servicios.repositorys.sf.UsuarioRepository;
import com.proyecto.servicios.security.JwtTokenService;
import com.proyecto.servicios.security.JwtTokenService.TokenEmitido;
import com.proyecto.servicios.service.CodigoVerificacionService.Desafio;
import com.proyecto.servicios.service.CodigoVerificacionService.Verificado;
import com.proyecto.servicios.service.Impl.AutenticacionServiceImpl;
import com.proyecto.servicios.service.exception.AutenticacionException;
import com.proyecto.servicios.service.exception.AutenticacionException.Tipo;
import com.proyecto.servicios.service.exception.NegocioException;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AutenticacionServiceTest {

    private static final String DESAFIO = "A".repeat(43);

    @Mock UsuarioRepository usuarios;
    @Mock JwtTokenService tokens;
    @Mock CodigoVerificacionService codigos;
    @Mock SesionService sesiones;
    @Mock TotpService totp;
    final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(4);
    AutenticacionService service;

    @BeforeEach
    void preparar() {
        service = new AutenticacionServiceImpl(usuarios, encoder, tokens, codigos, sesiones, totp);
    }

    private Usuario usuario() {
        Cliente c = new Cliente();
        c.setPrimerNombre("Ana");
        c.setApellidoPaterno("Pérez");
        c.setApellidoMaterno("Ruiz");
        Usuario u = new Usuario();
        u.setId(7);
        u.setCorreo("ana@example.com");
        u.setCliente(c);
        u.setPasswordHash(encoder.encode("Segura123!"));
        return u;
    }

    private static AutenticacionException.Tipo tipo(Runnable accion) {
        return assertThrows(AutenticacionException.class, accion::run).getTipo();
    }

    @Test
    void primerLoginEntregaElSecretoParaEnlazarLaAppYNoEmiteToken() {
        Usuario u = usuario();
        when(usuarios.findByCorreoIgnoreCase("ana@example.com")).thenReturn(Optional.of(u));
        when(codigos.emitir(7)).thenReturn(new Desafio(DESAFIO, Duration.ofMinutes(5)));
        when(totp.nuevoSecreto()).thenReturn("SECRETO");
        when(totp.uri("ana@example.com", "SECRETO")).thenReturn("otpauth://totp/x");

        var r = service.iniciarSesion(new LoginRequest(" ANA@EXAMPLE.COM ", "Segura123!"));

        assertEquals(DESAFIO, r.desafioId());
        assertEquals(300_000, r.expiraEnMs());
        assertEquals("SECRETO", r.enrolamiento().secreto());
        assertEquals("otpauth://totp/x", r.enrolamiento().otpauthUri());
        assertEquals("SECRETO", u.getTotpSecreto());
        verifyNoInteractions(tokens, sesiones);
    }

    @Test
    void siElSecretoAunNoSeConfirmoSeReutilizaEnLugarDeRegenerarlo() {
        Usuario u = usuario();
        u.setTotpSecreto("YAESCANEADO");
        when(usuarios.findByCorreoIgnoreCase("ana@example.com")).thenReturn(Optional.of(u));
        when(codigos.emitir(7)).thenReturn(new Desafio(DESAFIO, Duration.ofMinutes(5)));
        when(totp.uri("ana@example.com", "YAESCANEADO")).thenReturn("otpauth://totp/y");

        var r = service.iniciarSesion(new LoginRequest("ana@example.com", "Segura123!"));

        assertEquals("YAESCANEADO", r.enrolamiento().secreto());
        verify(totp, never()).nuevoSecreto();
    }

    @Test
    void conLaAppYaEnlazadaNoSeVuelveAEntregarElSecreto() {
        Usuario u = usuario();
        u.setTotpSecreto("SECRETO");
        u.setTotpConfirmado(true);
        when(usuarios.findByCorreoIgnoreCase("ana@example.com")).thenReturn(Optional.of(u));
        when(codigos.emitir(7)).thenReturn(new Desafio(DESAFIO, Duration.ofMinutes(5)));

        var r = service.iniciarSesion(new LoginRequest("ana@example.com", "Segura123!"));

        assertNull(r.enrolamiento());
        verifyNoInteractions(totp);
    }

    @Test
    void passwordIncorrectoNoEnviaCodigo() {
        when(usuarios.findByCorreoIgnoreCase("ana@example.com")).thenReturn(Optional.of(usuario()));
        assertEquals(Tipo.CREDENCIALES_INVALIDAS,
                tipo(() -> service.iniciarSesion(new LoginRequest("ana@example.com", "incorrecta"))));
        verifyNoInteractions(tokens, codigos, sesiones, totp);
    }

    @Test
    void usuarioInexistenteRespondeCredencialesInvalidasSinEnviarCodigo() {
        when(usuarios.findByCorreoIgnoreCase("nadie@example.com")).thenReturn(Optional.empty());
        assertEquals(Tipo.CREDENCIALES_INVALIDAS,
                tipo(() -> service.iniciarSesion(new LoginRequest("nadie@example.com", "Segura123!"))));
        verifyNoInteractions(tokens, codigos, sesiones, totp);
    }

    @Test
    void clienteOUsuarioInactivoNoRecibeCodigo() {
        Usuario clienteInactivo = usuario();
        clienteInactivo.getCliente().setActivo(false);
        Usuario usuarioInactivo = usuario();
        usuarioInactivo.setActivo(false);
        when(usuarios.findByCorreoIgnoreCase("ana@example.com"))
                .thenReturn(Optional.of(clienteInactivo), Optional.of(usuarioInactivo));
        for (int i = 0; i < 2; i++) {
            assertEquals(Tipo.CUENTA_INACTIVA,
                    tipo(() -> service.iniciarSesion(new LoginRequest("ana@example.com", "Segura123!"))));
        }
        verifyNoInteractions(tokens, codigos, sesiones, totp);
    }

    @Test
    void codigoCorrectoAbreLaSesionYConsumeElDesafio() {
        when(codigos.verificar(DESAFIO, "123456")).thenReturn(new Verificado(usuario(), 42));
        TokenEmitido token = new TokenEmitido("jwt", "jti-1", Instant.now().plusSeconds(60));
        when(tokens.emitir("7")).thenReturn(token);
        when(tokens.getExpirationMs()).thenReturn(60000L);
        when(sesiones.abrir(7, token, false)).thenReturn(true);

        var r = service.verificarCodigo(new VerificacionCodigoRequest(DESAFIO, "123456", false));

        assertEquals("jwt", r.token());
        assertEquals("Bearer", r.tipo());
        assertEquals(60000, r.expiraEnMs());
        assertEquals("ana@example.com", r.usuario());
        assertEquals("Ana Pérez Ruiz", r.nombreCompleto());
        verify(codigos).consumir(DESAFIO);
    }

    @Test
    void alAbrirLaSesionElCodigoQuedaUsadoYLaAppEnlazada() {
        Usuario u = usuario();
        when(codigos.verificar(DESAFIO, "123456")).thenReturn(new Verificado(u, 42));
        TokenEmitido token = new TokenEmitido("jwt", "jti-1", Instant.now().plusSeconds(60));
        when(tokens.emitir("7")).thenReturn(token);
        when(sesiones.abrir(7, token, false)).thenReturn(true);

        service.verificarCodigo(new VerificacionCodigoRequest(DESAFIO, "123456", false));

        assertEquals(42, u.getTotpUltimoPaso());
        assertTrue(u.isTotpConfirmado());
    }

    @Test
    void conSesionActivaElPasoNoSeAnotaParaPoderReintentarConElMismoCodigo() {
        Usuario u = usuario();
        when(codigos.verificar(DESAFIO, "123456")).thenReturn(new Verificado(u, 42));
        TokenEmitido token = new TokenEmitido("jwt", "jti-1", Instant.now().plusSeconds(60));
        when(tokens.emitir("7")).thenReturn(token);
        when(sesiones.abrir(7, token, false)).thenReturn(false);

        assertThrows(NegocioException.class,
                () -> service.verificarCodigo(new VerificacionCodigoRequest(DESAFIO, "123456", false)));

        assertEquals(0, u.getTotpUltimoPaso());
        assertFalse(u.isTotpConfirmado());
    }

    @Test
    void conSesionActivaSinConfirmarResponde409YConservaElDesafio() {
        when(codigos.verificar(DESAFIO, "123456")).thenReturn(new Verificado(usuario(), 42));
        TokenEmitido token = new TokenEmitido("jwt", "jti-1", Instant.now().plusSeconds(60));
        when(tokens.emitir("7")).thenReturn(token);
        when(sesiones.abrir(7, token, false)).thenReturn(false);

        NegocioException e = assertThrows(NegocioException.class,
                () -> service.verificarCodigo(new VerificacionCodigoRequest(DESAFIO, "123456", false)));

        assertEquals(HttpStatus.CONFLICT, e.getStatus());
        verify(codigos, never()).consumir(any());
    }

    @Test
    void confirmandoElCierreReemplazaLaSesionAnterior() {
        when(codigos.verificar(DESAFIO, "123456")).thenReturn(new Verificado(usuario(), 42));
        TokenEmitido token = new TokenEmitido("jwt", "jti-2", Instant.now().plusSeconds(60));
        when(tokens.emitir("7")).thenReturn(token);
        when(sesiones.abrir(7, token, true)).thenReturn(true);

        service.verificarCodigo(new VerificacionCodigoRequest(DESAFIO, "123456", true));

        verify(codigos).consumir(DESAFIO);
    }

    @Test
    void codigoIncorrectoNoEmiteTokenNiTocaLaSesion() {
        when(codigos.verificar(DESAFIO, "000000"))
                .thenThrow(new NegocioException(HttpStatus.UNAUTHORIZED, "Código de verificación incorrecto"));
        assertThrows(NegocioException.class,
                () -> service.verificarCodigo(new VerificacionCodigoRequest(DESAFIO, "000000", true)));
        verifyNoInteractions(tokens, sesiones);
    }

    @Test
    void cerrarSesionEliminaLaSesionDelUsuario() {
        service.cerrarSesion(7);
        verify(sesiones).cerrar(7);
    }
}
