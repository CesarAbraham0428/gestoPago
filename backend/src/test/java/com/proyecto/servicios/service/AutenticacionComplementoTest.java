package com.proyecto.servicios.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.proyecto.servicios.entity.sf.Cliente;
import com.proyecto.servicios.entity.sf.Usuario;
import com.proyecto.servicios.models.request.VerificacionCodigoRequest;
import com.proyecto.servicios.models.response.AuthResponse;
import com.proyecto.servicios.repositorys.sf.UsuarioRepository;
import com.proyecto.servicios.security.JwtTokenService;
import com.proyecto.servicios.security.JwtTokenService.TokenEmitido;
import com.proyecto.servicios.service.Impl.AutenticacionServiceImpl;
import com.proyecto.servicios.service.exception.AutenticacionException;
import com.proyecto.servicios.service.exception.NegocioException;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

class AutenticacionComplementoTest {

    private static final String DESAFIO = "A".repeat(43);
    private final UsuarioRepository usuarios = mock(UsuarioRepository.class);
    private final JwtTokenService tokens = mock(JwtTokenService.class);
    private final CodigoVerificacionService codigos = mock(CodigoVerificacionService.class);
    private final SesionService sesiones = mock(SesionService.class);
    private final AutenticacionServiceImpl servicio =
            new AutenticacionServiceImpl(usuarios, new BCryptPasswordEncoder(4), tokens, codigos, sesiones);

    private static Usuario usuario(String primerNombre, String segundoNombre, String correo) {
        Cliente cliente = new Cliente();
        cliente.setPrimerNombre(primerNombre);
        cliente.setSegundoNombre(segundoNombre);
        Usuario usuario = new Usuario();
        usuario.setId(7);
        usuario.setCorreo(correo);
        usuario.setCliente(cliente);
        return usuario;
    }

    private static VerificacionCodigoRequest verificacion(boolean cerrarAnterior) {
        return new VerificacionCodigoRequest(DESAFIO, "123456", cerrarAnterior);
    }

    @Test
    void siElUsuarioDelDesafioYaNoExisteSeRechazanLasCredenciales() {
        when(codigos.verificar(DESAFIO, "123456")).thenReturn(7);
        when(usuarios.findById(7)).thenReturn(Optional.empty());

        AutenticacionException error =
                assertThrows(AutenticacionException.class, () -> servicio.verificarCodigo(verificacion(false)));

        assertEquals(AutenticacionException.Tipo.CREDENCIALES_INVALIDAS, error.getTipo());
        verify(tokens, never()).emitir(any());
    }

    @Test
    void unUsuarioOClienteInactivoNoObtieneSesion() {
        Usuario inactivo = usuario("Ana", null, "ana@example.com");
        inactivo.setActivo(false);
        when(codigos.verificar(DESAFIO, "123456")).thenReturn(7);
        when(usuarios.findById(7)).thenReturn(Optional.of(inactivo));
        assertEquals(AutenticacionException.Tipo.CUENTA_INACTIVA, assertThrows(AutenticacionException.class,
                () -> servicio.verificarCodigo(verificacion(false))).getTipo());

        Usuario clienteInactivo = usuario("Ana", null, "ana@example.com");
        clienteInactivo.getCliente().setActivo(false);
        when(usuarios.findById(7)).thenReturn(Optional.of(clienteInactivo));
        assertThrows(AutenticacionException.class, () -> servicio.verificarCodigo(verificacion(false)));
    }

    @Test
    void conUnaSesionActivaSinPermisoParaCerrarlaSeResponde409YElCodigoNoSeConsume() {
        when(codigos.verificar(DESAFIO, "123456")).thenReturn(7);
        when(usuarios.findById(7)).thenReturn(Optional.of(usuario("Ana", null, "ana@example.com")));
        when(tokens.emitir("7")).thenReturn(new TokenEmitido("jwt", "jti", Instant.now()));
        when(sesiones.abrir(any(), any(), anyBoolean())).thenReturn(false);

        NegocioException error =
                assertThrows(NegocioException.class, () -> servicio.verificarCodigo(verificacion(false)));

        assertEquals(HttpStatus.CONFLICT, error.getStatus());
        verify(codigos, never()).consumir(any());
    }

    @Test
    void unaVerificacionCorrectaAbreLaSesionConsumeElCodigoYArmaElNombre() {
        when(codigos.verificar(DESAFIO, "123456")).thenReturn(7);
        when(usuarios.findById(7)).thenReturn(Optional.of(usuario("Ana", "María", "ana@example.com")));
        TokenEmitido emitido = new TokenEmitido("jwt", "jti", Instant.now());
        when(tokens.emitir("7")).thenReturn(emitido);
        when(tokens.getExpirationMs()).thenReturn(60_000L);
        when(sesiones.abrir(7, emitido, true)).thenReturn(true);

        AuthResponse respuesta = servicio.verificarCodigo(verificacion(true));

        assertEquals("jwt", respuesta.token());
        assertEquals("Bearer", respuesta.tipo());
        assertEquals("Ana María", respuesta.nombreCompleto());
        verify(codigos).consumir(DESAFIO);
    }

    @Test
    void sinNingunNombreElNombreCompletoEsVacio() {
        when(codigos.verificar(DESAFIO, "123456")).thenReturn(7);
        when(usuarios.findById(7)).thenReturn(Optional.of(usuario(null, null, "ana@example.com")));
        TokenEmitido emitido = new TokenEmitido("jwt", "jti", Instant.now());
        when(tokens.emitir("7")).thenReturn(emitido);
        when(sesiones.abrir(7, emitido, false)).thenReturn(true);

        assertEquals("", servicio.verificarCodigo(verificacion(false)).nombreCompleto());
    }

    @Test
    void cerrarSesionLaEliminaDelRegistro() {
        servicio.cerrarSesion(7);

        verify(sesiones).cerrar(7);
    }
}
