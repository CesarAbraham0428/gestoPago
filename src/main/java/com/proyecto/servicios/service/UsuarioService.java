package com.proyecto.servicios.service;

import com.proyecto.servicios.entity.sf.Cliente;
import com.proyecto.servicios.entity.sf.Usuario;
import com.proyecto.servicios.mapper.ClienteMapper;
import com.proyecto.servicios.models.request.CambioPasswordRequest;
import com.proyecto.servicios.models.request.UsuarioConsultaRequest;
import com.proyecto.servicios.models.response.ListaGenericResponse;
import com.proyecto.servicios.models.response.UsuarioResponse;
import com.proyecto.servicios.repositorys.sf.ClientesRepository;
import com.proyecto.servicios.repositorys.sf.UsuarioRepository;
import com.proyecto.servicios.service.exception.ClienteNoEncontradoException;
import com.proyecto.servicios.service.exception.CredencialesInvalidasException;
import com.proyecto.servicios.service.exception.UsuarioInactivoException;
import com.proyecto.servicios.service.exception.UsuarioNoEncontradoException;
import com.proyecto.servicios.validation.PasswordValidator;
import jakarta.persistence.EntityManager;
import java.util.Locale;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UsuarioService {

    private final UsuarioRepository usuarios;
    private final ClientesRepository clientes;
    private final PasswordEncoder encoder;
    private final EntityManager em;
    private final AutorizacionService autorizacion;

    public UsuarioService(UsuarioRepository usuarios, ClientesRepository clientes, PasswordEncoder encoder,
            EntityManager em, AutorizacionService autorizacion) {
        this.usuarios = usuarios;
        this.clientes = clientes;
        this.encoder = encoder;
        this.em = em;
        this.autorizacion = autorizacion;
    }

    @Transactional(readOnly = true)
    public ListaGenericResponse<UsuarioResponse> consultar(UsuarioConsultaRequest request,
            Integer usuarioAutenticadoId) {
        Integer propietarioId = autorizacion.clienteIdParaConsulta(usuarioAutenticadoId);
        String rfc = request.rfc() == null ? null : request.rfc().trim().toUpperCase(Locale.ROOT);
        String curp = request.curp() == null ? null : request.curp().trim().toUpperCase(Locale.ROOT);
        String correo = request.correo() == null ? null : request.correo().trim().toLowerCase(Locale.ROOT);
        String nombre = request.nombre() == null ? null : "%" + request.nombre().trim().toLowerCase(Locale.ROOT) + "%";
        var pagina = PageRequest.of(request.pagina(), request.tamanio(), Sort.by("id"));
        return ListaGenericResponse.desde(usuarios.consultar(rfc, curp, correo,
                nombre, request.activo(), propietarioId, pagina));
    }

    @Transactional(readOnly = true)
    public UsuarioResponse obtener(Integer usuarioAutenticadoId) {
        Usuario usuario = buscar(usuarioAutenticadoId);
        validarActivo(usuario, usuario.getCliente());
        return ClienteMapper.usuario(usuario);
    }

    @Transactional
    public void cambiarPassword(Integer usuarioAutenticadoId, CambioPasswordRequest request) {
        PasswordValidator.validar(request.passwordNueva());
        Usuario usuario = buscar(usuarioAutenticadoId);
        Cliente cliente = clientes.bloquearPorId(usuario.getCliente().getId())
                .orElseThrow(ClienteNoEncontradoException::new);
        em.refresh(usuario);
        validarActivo(usuario, cliente);
        if (!encoder.matches(request.passwordActual(), usuario.getPasswordHash())) {
            throw new CredencialesInvalidasException();
        }
        usuario.setPasswordHash(encoder.encode(request.passwordNueva()));
        em.flush();
    }

    private Usuario buscar(Integer usuarioAutenticadoId) {
        return usuarios.findById(usuarioAutenticadoId).orElseThrow(UsuarioNoEncontradoException::new);
    }

    private void validarActivo(Usuario usuario, Cliente cliente) {
        if (!usuario.isActivo() || !cliente.isActivo()) {
            throw new UsuarioInactivoException();
        }
    }
}
