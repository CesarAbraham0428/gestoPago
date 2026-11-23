package com.proyecto.servicios.service;

import com.proyecto.servicios.entity.sf.Cliente;
import com.proyecto.servicios.entity.sf.Rol;
import com.proyecto.servicios.entity.sf.Usuario;
import com.proyecto.servicios.repositorys.sf.UsuarioRepository;
import com.proyecto.servicios.service.exception.NegocioException;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AutorizacionServiceTest {

    private final UsuarioRepository usuarios = mock(UsuarioRepository.class);
    private final AutorizacionService service = new AutorizacionService(usuarios);

    @Test
    void permisosSeDeterminanPorRelacionYRolGuardadosEnBaseDeDatos() {
        Usuario usuario = usuario(Rol.Cliente);
        when(usuarios.findById(7)).thenReturn(Optional.of(usuario));

        assertEquals(3, service.clienteIdParaConsulta(7));
        assertDoesNotThrow(() -> service.autorizarCliente(3, 7));
        var exception = assertThrows(NegocioException.class, () -> service.autorizarCliente(9, 7));
        assertEquals(HttpStatus.FORBIDDEN, exception.getStatus());
    }

    @Test
    void administradorActivoTieneAccesoGlobal() {
        when(usuarios.findById(7)).thenReturn(Optional.of(usuario(Rol.Administrador)));

        assertNull(service.clienteIdParaConsulta(7));
        assertDoesNotThrow(() -> service.autorizarCliente(9, 7));
    }

    @Test
    void clienteInactivoNoObtienePermisosAunqueSuUsuarioEsteActivo() {
        Usuario usuario = usuario(Rol.Administrador);
        usuario.getCliente().setActivo(false);
        when(usuarios.findById(7)).thenReturn(Optional.of(usuario));

        var exception = assertThrows(NegocioException.class, () -> service.clienteIdParaConsulta(7));
        assertEquals(HttpStatus.FORBIDDEN, exception.getStatus());
    }

    @Test
    void usuarioEliminadoNoObtienePermisos() {
        when(usuarios.findById(7)).thenReturn(Optional.empty());

        var exception = assertThrows(NegocioException.class, () -> service.autorizarCliente(3, 7));
        assertEquals(HttpStatus.FORBIDDEN, exception.getStatus());
    }

    private Usuario usuario(Rol rol) {
        Cliente cliente = new Cliente();
        cliente.setId(3);
        cliente.setRol(rol);
        Usuario usuario = new Usuario();
        usuario.setId(7);
        usuario.setCliente(cliente);
        return usuario;
    }
}
