package com.proyecto.servicios.service;

import com.proyecto.servicios.entity.sf.Cliente;
import com.proyecto.servicios.entity.sf.Usuario;
import com.proyecto.servicios.models.request.CambioPasswordRequest;
import com.proyecto.servicios.models.request.UsuarioConsultaRequest;
import com.proyecto.servicios.models.response.UsuarioResponse;
import com.proyecto.servicios.repositorys.sf.ClientesRepository;
import com.proyecto.servicios.repositorys.sf.UsuarioRepository;
import com.proyecto.servicios.service.exception.CredencialesInvalidasException;
import com.proyecto.servicios.service.exception.PasswordInvalidaException;
import com.proyecto.servicios.service.exception.UsuarioInactivoException;
import com.proyecto.servicios.service.exception.UsuarioNoEncontradoException;
import jakarta.persistence.EntityManager;
import java.util.Optional;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

@ExtendWith(MockitoExtension.class)
class UsuarioServiceTest {

    @Mock private UsuarioRepository usuarios;
    @Mock private ClientesRepository clientes;
    @Mock private EntityManager em;
    @Mock private AutorizacionService autorizacion;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(4);
    private UsuarioService service;

    @BeforeEach
    void crearServicio() {
        service = new UsuarioService(usuarios, clientes, encoder, em, autorizacion);
    }

    @Test
    void cambioDePasswordGuardaBCryptYVerificaPasswordActual() {
        Usuario usuario = usuario();
        preparar(usuario);

        service.cambiarPassword(7, new CambioPasswordRequest("Actual123!", "Nueva123!"));

        assertTrue(encoder.matches("Nueva123!", usuario.getPasswordHash()));
        assertFalse(encoder.matches("Actual123!", usuario.getPasswordHash()));
        assertNotEquals("Nueva123!", usuario.getPasswordHash());
        verify(usuarios).findById(7);
        verifyNoMoreInteractions(usuarios);
        verify(em).flush();
    }

    @Test
    void noPermitePasswordActualIncorrecta() {
        Usuario usuario = usuario();
        preparar(usuario);
        String hash = usuario.getPasswordHash();

        assertThrows(CredencialesInvalidasException.class, () -> service.cambiarPassword(7,
                new CambioPasswordRequest("Incorrecta123!", "Nueva123!")));

        assertEquals(hash, usuario.getPasswordHash());
        verify(em, never()).flush();
    }

    @Test
    void noPermitePasswordNuevaDebil() {
        assertThrows(PasswordInvalidaException.class, () -> service.cambiarPassword(7,
                new CambioPasswordRequest("Actual123!", "abcdefgh")));
        verifyNoInteractions(usuarios, em);
    }

    @ParameterizedTest
    @ValueSource(strings = {"usuario", "cliente"})
    void noPermiteCambioParaUsuarioOClienteInactivo(String entidad) {
        Usuario usuario = usuario();
        preparar(usuario);
        if (entidad.equals("usuario")) {
            usuario.setActivo(false);
        } else {
            usuario.getCliente().setActivo(false);
        }
        String hashOriginal = usuario.getPasswordHash();

        assertThrows(UsuarioInactivoException.class, () -> service.cambiarPassword(7,
                new CambioPasswordRequest("Actual123!", "Nueva123!")));

        assertEquals(hashOriginal, usuario.getPasswordHash());
        verify(em, never()).flush();
    }

    @Test
    void obtenerConsultaSolamenteLaIdentidadAutenticada() {
        Usuario usuario = usuario();
        when(usuarios.findById(7)).thenReturn(Optional.of(usuario));

        var response = service.obtener(7);

        assertEquals(7, response.id());
        assertEquals(3, response.clienteId());
        assertEquals("ana@example.com", response.correo());
        assertTrue(response.activo());
        verify(usuarios).findById(7);
        verifyNoMoreInteractions(usuarios);
    }

    @Test
    void consultaDeUsuarioInactivoDevuelveError() {
        Usuario usuario = usuario();
        usuario.setActivo(false);
        when(usuarios.findById(7)).thenReturn(Optional.of(usuario));

        assertThrows(UsuarioInactivoException.class, () -> service.obtener(7));
    }

    @Test
    void usuarioInexistenteDevuelveExcepcionPersonalizada() {
        when(usuarios.findById(7)).thenReturn(Optional.empty());

        assertThrows(UsuarioNoEncontradoException.class, () -> service.obtener(7));
    }

    @Test
    void consultaNormalizaFiltrosYAplicaPropietarioAntesDePaginar() {
        var request = new UsuarioConsultaRequest("pera900101ab1", "pera900101mdfrzn01", "ANA@EXAMPLE.COM", "Ana", true, 0, 1);
        var pagina = PageRequest.of(0, 1, Sort.by("id"));
        when(autorizacion.clienteIdParaConsulta(7)).thenReturn(3);
        when(usuarios.consultar("PERA900101AB1", "PERA900101MDFRZN01", "ana@example.com", "%ana%", true, 3, pagina))
                .thenReturn(new PageImpl<>(List.of(new UsuarioResponse(7, 3, "ana@example.com", true, null, null)),
                        pagina, 1));
        var response = service.consultar(request, 7);
        assertEquals(1, response.totalElementos());
        assertEquals(7, response.datos().get(0).id());
        verify(usuarios).consultar("PERA900101AB1", "PERA900101MDFRZN01", "ana@example.com", "%ana%", true, 3, pagina);
    }

    @Test
    void consultaNoAccedeAlRepositorioSiLaAutorizacionFalla() {
        when(autorizacion.clienteIdParaConsulta(7)).thenThrow(new UsuarioInactivoException());
        var request = new UsuarioConsultaRequest(null, null, null, null, null, null, null);
        assertThrows(UsuarioInactivoException.class, () -> service.consultar(request, 7));
        verifyNoInteractions(usuarios);
    }

    private Usuario usuario() {
        Cliente cliente = new Cliente();
        cliente.setId(3);
        Usuario usuario = new Usuario();
        usuario.setId(7);
        usuario.setCliente(cliente);
        usuario.setCorreo("ana@example.com");
        usuario.setPasswordHash(encoder.encode("Actual123!"));
        return usuario;
    }

    private void preparar(Usuario usuario) {
        when(usuarios.findById(7)).thenReturn(Optional.of(usuario));
        when(clientes.bloquearPorId(3)).thenReturn(Optional.of(usuario.getCliente()));
    }
}
