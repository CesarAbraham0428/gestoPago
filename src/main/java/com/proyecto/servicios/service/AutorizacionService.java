package com.proyecto.servicios.service;

import com.proyecto.servicios.entity.sf.Cliente;
import com.proyecto.servicios.entity.sf.Rol;
import com.proyecto.servicios.entity.sf.Usuario;
import com.proyecto.servicios.repositorys.sf.UsuarioRepository;
import com.proyecto.servicios.service.exception.NegocioException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class AutorizacionService {

    private final UsuarioRepository usuarios;

    public AutorizacionService(UsuarioRepository usuarios) {
        this.usuarios = usuarios;
    }

    public Integer clienteIdParaConsulta(Integer usuarioAutenticadoId) {
        Cliente cliente = usuarioActivo(usuarioAutenticadoId).getCliente();
        return cliente.getRol() == Rol.Administrador ? null : cliente.getId();
    }

    public void autorizarCliente(Integer clienteId, Integer usuarioAutenticadoId) {
        Cliente propietario = usuarioActivo(usuarioAutenticadoId).getCliente();
        if (propietario.getRol() != Rol.Administrador && !propietario.getId().equals(clienteId)) {
            throw accesoDenegado();
        }
    }

    private Usuario usuarioActivo(Integer usuarioAutenticadoId) {
        if (usuarioAutenticadoId == null) {
            throw new NegocioException(HttpStatus.UNAUTHORIZED, "Usuario no autenticado");
        }
        Usuario usuario = usuarios.findById(usuarioAutenticadoId).orElseThrow(this::accesoDenegado);
        if (!usuario.isActivo() || usuario.getCliente() == null || !usuario.getCliente().isActivo()) {
            throw accesoDenegado();
        }
        return usuario;
    }

    private NegocioException accesoDenegado() {
        return new NegocioException(HttpStatus.FORBIDDEN, "No tienes permiso para acceder a este cliente");
    }
}
