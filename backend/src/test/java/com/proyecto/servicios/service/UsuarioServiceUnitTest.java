package com.proyecto.servicios.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.proyecto.servicios.entity.sf.Cliente;
import com.proyecto.servicios.entity.sf.Usuario;
import com.proyecto.servicios.models.request.CambioPasswordRequest;
import com.proyecto.servicios.models.request.UsuarioConsultaRequest;
import com.proyecto.servicios.models.response.UsuarioResponse;
import com.proyecto.servicios.repositorys.sf.ClientesRepository;
import com.proyecto.servicios.repositorys.sf.UsuarioRepository;
import com.proyecto.servicios.service.exception.ClienteNoEncontradoException;
import com.proyecto.servicios.service.exception.CredencialesInvalidasException;
import com.proyecto.servicios.service.exception.UsuarioInactivoException;
import com.proyecto.servicios.service.exception.UsuarioNoEncontradoException;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;

class UsuarioServiceUnitTest {

    private static final String PASSWORD_NUEVA = "Nueva#2026";

    private final UsuarioRepository usuarios = mock(UsuarioRepository.class);
    private final ClientesRepository clientes = mock(ClientesRepository.class);
    private final PasswordEncoder encoder = mock(PasswordEncoder.class);
    private final EntityManager em = mock(EntityManager.class);
    private final AutorizacionService autorizacion = mock(AutorizacionService.class);
    private final SesionService sesiones = mock(SesionService.class);
    private final UsuarioService servicio =
            new UsuarioService(usuarios, clientes, encoder, em, autorizacion, sesiones);

    private Usuario usuario(boolean usuarioActivo, boolean clienteActivo) {
        Cliente cliente = new Cliente();
        cliente.setId(7);
        cliente.setActivo(clienteActivo);
        Usuario u = new Usuario();
        u.setId(3);
        u.setCliente(cliente);
        u.setCorreo("ana@correo.mx");
        u.setPasswordHash("hash");
        u.setActivo(usuarioActivo);
        return u;
    }

    @Test
    void consultarNormalizaLosFiltrosYRespetaAlPropietario() {
        when(autorizacion.clienteIdParaConsulta(3)).thenReturn(7);
        Page<UsuarioResponse> pagina = new PageImpl<>(List.of());
        when(usuarios.consultar(any(), any(), any(), any(), any(), any(), any(Pageable.class))).thenReturn(pagina);

        var respuesta = servicio.consultar(new UsuarioConsultaRequest(" abc ", " def ", " ANA@Correo.MX ",
                " Ana ", true, 1, 5), 3);

        assertEquals(0, respuesta.totalElementos());
        verify(usuarios).consultar(eq("ABC"), eq("DEF"), eq("ana@correo.mx"), eq("%ana%"), eq(true), eq(7),
                any(Pageable.class));
    }

    @Test
    void consultarSinFiltrosEnviaNulos() {
        when(autorizacion.clienteIdParaConsulta(3)).thenReturn(null);
        when(usuarios.consultar(any(), any(), any(), any(), any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        servicio.consultar(new UsuarioConsultaRequest(null, null, null, null, null, null, null), 3);

        verify(usuarios).consultar(isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), any(Pageable.class));
    }

    @Test
    void obtenerDevuelveAlUsuarioActivo() {
        when(usuarios.findById(3)).thenReturn(Optional.of(usuario(true, true)));

        assertEquals("ana@correo.mx", servicio.obtener(3).correo());
    }

    @Test
    void obtenerRechazaUsuarioOClienteInactivoOInexistente() {
        when(usuarios.findById(3)).thenReturn(Optional.of(usuario(false, true)));
        assertThrows(UsuarioInactivoException.class, () -> servicio.obtener(3));

        when(usuarios.findById(3)).thenReturn(Optional.of(usuario(true, false)));
        assertThrows(UsuarioInactivoException.class, () -> servicio.obtener(3));

        when(usuarios.findById(9)).thenReturn(Optional.empty());
        assertThrows(UsuarioNoEncontradoException.class, () -> servicio.obtener(9));
    }

    @Test
    void cambiarPasswordGuardaElHashNuevoYCierraLaSesion() {
        Usuario u = usuario(true, true);
        when(usuarios.findById(3)).thenReturn(Optional.of(u));
        when(clientes.bloquearPorId(7)).thenReturn(Optional.of(u.getCliente()));
        when(encoder.matches("actual", "hash")).thenReturn(true);
        when(encoder.encode(PASSWORD_NUEVA)).thenReturn("hash-nuevo");

        servicio.cambiarPassword(3, new CambioPasswordRequest("actual", PASSWORD_NUEVA));

        assertEquals("hash-nuevo", u.getPasswordHash());
        verify(em).flush();
        verify(sesiones).cerrar(3);
    }

    @Test
    void cambiarPasswordRechazaLaContrasenaActualIncorrecta() {
        Usuario u = usuario(true, true);
        when(usuarios.findById(3)).thenReturn(Optional.of(u));
        when(clientes.bloquearPorId(7)).thenReturn(Optional.of(u.getCliente()));
        when(encoder.matches("mala", "hash")).thenReturn(false);

        assertThrows(CredencialesInvalidasException.class,
                () -> servicio.cambiarPassword(3, new CambioPasswordRequest("mala", PASSWORD_NUEVA)));

        verify(sesiones, never()).cerrar(any());
    }

    @Test
    void cambiarPasswordRechazaInactivosYClientesInexistentes() {
        Usuario inactivo = usuario(false, true);
        when(usuarios.findById(3)).thenReturn(Optional.of(inactivo));
        when(clientes.bloquearPorId(7)).thenReturn(Optional.of(inactivo.getCliente()));
        assertThrows(UsuarioInactivoException.class,
                () -> servicio.cambiarPassword(3, new CambioPasswordRequest("x", PASSWORD_NUEVA)));

        when(clientes.bloquearPorId(7)).thenReturn(Optional.empty());
        assertThrows(ClienteNoEncontradoException.class,
                () -> servicio.cambiarPassword(3, new CambioPasswordRequest("x", PASSWORD_NUEVA)));
    }
}
