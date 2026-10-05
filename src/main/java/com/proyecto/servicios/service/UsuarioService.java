package com.proyecto.servicios.service;
import com.proyecto.servicios.entity.sf.*;
import com.proyecto.servicios.model.*;
import com.proyecto.servicios.repositorys.sf.*;
import com.proyecto.servicios.service.exception.NegocioException;
import com.proyecto.servicios.validation.PasswordValidator;
import jakarta.persistence.EntityManager;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service
public class UsuarioService {
    private final UsuarioRepository usuarios;
    private final ClientesRepository clientes;
    private final PasswordEncoder encoder;
    private final EntityManager em;
    public UsuarioService(UsuarioRepository usuarios,ClientesRepository clientes,PasswordEncoder encoder,EntityManager em) {
        this.usuarios=usuarios; this.clientes=clientes; this.encoder=encoder; this.em=em;
    }
    @Transactional(readOnly=true)
    public UsuarioResponse obtener(Integer id,Integer solicitante) {
        autorizar(id,solicitante); return ClienteMapper.usuario(buscar(id));
    }
    @Transactional
    public void cambiarPassword(Integer id,Integer solicitante,CambioPasswordRequest r) {
        autorizar(id,solicitante); PasswordValidator.validar(r.passwordNueva());
        Usuario u=buscar(id);
        Cliente c=clientes.bloquearPorId(u.getCliente().getId()).orElseThrow(() -> error(HttpStatus.NOT_FOUND,"Cliente no encontrado"));
        em.refresh(u);
        if(!c.isActivo() || !u.isActivo()) throw error(HttpStatus.FORBIDDEN,"Usuario inactivo");
        if(!encoder.matches(r.passwordActual(),u.getPasswordHash())) throw error(HttpStatus.UNAUTHORIZED,"Credenciales inválidas");
        u.setPasswordHash(encoder.encode(r.passwordNueva())); em.flush();
    }
    private Usuario buscar(Integer id) {
        return usuarios.findById(id).orElseThrow(() -> error(HttpStatus.NOT_FOUND,"Usuario no encontrado"));
    }
    private void autorizar(Integer id,Integer solicitante) {
        if(!id.equals(solicitante)) throw error(HttpStatus.FORBIDDEN,"Solo puedes acceder a tu usuario");
    }
    private NegocioException error(HttpStatus s,String m) { return new NegocioException(s,m); }
}
