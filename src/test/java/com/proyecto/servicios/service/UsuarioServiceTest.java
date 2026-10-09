package com.proyecto.servicios.service;

import com.proyecto.servicios.entity.sf.Cliente;
import com.proyecto.servicios.entity.sf.Usuario;
import com.proyecto.servicios.model.CambioPasswordRequest;
import com.proyecto.servicios.repositorys.sf.*;
import com.proyecto.servicios.service.exception.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UsuarioServiceTest {
    @Mock private UsuarioRepository usuarios;
    @Mock private ClientesRepository clientes;
    @Mock private EntityManager em;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(4);
    private UsuarioService service;
    @BeforeEach void crearServicio() { service = new UsuarioService(usuarios, clientes, encoder, em); }

    private Usuario usuario() {
        Cliente c = new Cliente(); c.setId(3);
        Usuario u = new Usuario(); u.setId(7); u.setCliente(c); u.setCorreo("ana@example.com");
        u.setPasswordHash(encoder.encode("Actual123!"));
        return u;
    }
    @Test void cambioDePasswordGuardaBCryptYVerificaPasswordActual() {
        Usuario u = usuario(); preparar(u);
        service.cambiarPassword(7, 7, new CambioPasswordRequest("Actual123!", "Nueva123!"));
        assertTrue(encoder.matches("Nueva123!", u.getPasswordHash()));
        assertFalse(encoder.matches("Actual123!", u.getPasswordHash()));
        assertNotEquals("Nueva123!", u.getPasswordHash());
        verify(em).flush();
    }
    @Test void noPermiteCambiarPasswordAjena() {
        var exception = assertThrows(NegocioException.class, () -> service.cambiarPassword(7, 8,
            new CambioPasswordRequest("Actual123!", "Nueva123!")));
        assertEquals(HttpStatus.FORBIDDEN, exception.getStatus());
        verifyNoInteractions(usuarios, em);
    }
    @Test void noPermitePasswordActualIncorrecta() {
        Usuario u = usuario(); preparar(u); String hash = u.getPasswordHash();
        assertThrows(CredencialesInvalidasException.class, () -> service.cambiarPassword(7, 7,
            new CambioPasswordRequest("Incorrecta123!", "Nueva123!")));
        assertEquals(hash, u.getPasswordHash()); verify(em, never()).flush();
    }
    @Test void noPermitePasswordNuevaDebil() {
        assertThrows(PasswordInvalidaException.class, () -> service.cambiarPassword(7, 7,
            new CambioPasswordRequest("Actual123!", "abcdefgh")));
        verifyNoInteractions(usuarios, em);
    }
    @ParameterizedTest @ValueSource(strings={"usuario", "cliente"})
    void noPermiteCambioParaUsuarioOClienteInactivo(String entidad) {
        Usuario u = usuario(); preparar(u);
        if (entidad.equals("usuario")) u.setActivo(false);
        else u.getCliente().setActivo(false);
        String hashOriginal = u.getPasswordHash();
        assertThrows(UsuarioInactivoException.class, () -> service.cambiarPassword(7, 7,
            new CambioPasswordRequest("Actual123!", "Nueva123!")));
        assertEquals(hashOriginal, u.getPasswordHash());
        verify(em, never()).flush();
    }
    @Test void obtenerDevuelveDatosDelPropioUsuario() {
        Usuario u = usuario();
        when(usuarios.findById(7)).thenReturn(Optional.of(u));
        var response = service.obtener(7, 7);
        assertEquals(7, response.id());
        assertEquals(3, response.clienteId());
        assertEquals("ana@example.com", response.correo());
        assertTrue(response.activo());
    }
    @Test void obtenerUsuarioAjenoDevuelve403SinConsultarElRepositorio() {
        var exception = assertThrows(NegocioException.class, () -> service.obtener(7, 8));
        assertEquals(HttpStatus.FORBIDDEN, exception.getStatus());
        verifyNoInteractions(usuarios);
    }
    @Test void usuarioInexistenteDevuelveExcepcionPersonalizada() {
        when(usuarios.findById(7)).thenReturn(Optional.empty());
        assertThrows(UsuarioNoEncontradoException.class, () -> service.obtener(7, 7));
    }
    private void preparar(Usuario u) {
        when(usuarios.findById(7)).thenReturn(Optional.of(u));
        when(clientes.bloquearPorId(3)).thenReturn(Optional.of(u.getCliente()));
    }
}
