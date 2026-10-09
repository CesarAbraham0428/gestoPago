package com.proyecto.servicios.service.Impl;
import com.proyecto.servicios.entity.sf.*;
import com.proyecto.servicios.model.*;
import com.proyecto.servicios.repositorys.sf.UsuarioRepository;
import com.proyecto.servicios.security.JwtTokenService;
import com.proyecto.servicios.service.AutenticacionService;
import com.proyecto.servicios.service.exception.AutenticacionException;
import com.proyecto.servicios.service.exception.AutenticacionException.Tipo;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.stream.Stream;
import java.util.Objects;
@Service
public class AutenticacionServiceImpl implements AutenticacionService {
    private final UsuarioRepository usuarios;
    private final PasswordEncoder encoder;
    private final JwtTokenService tokens;
    public AutenticacionServiceImpl(UsuarioRepository usuarios,PasswordEncoder encoder,JwtTokenService tokens) {
        this.usuarios=usuarios; this.encoder=encoder; this.tokens=tokens;
    }

    @Override @Transactional(readOnly=true)

    public AuthResponse iniciarSesion(LoginRequest r) {
        
        Usuario u=usuarios.findByCorreoIgnoreCase(ClienteServiceImpl.normalizarCorreo(r.correo()))
            .orElseThrow(() -> new AutenticacionException(Tipo.CREDENCIALES_INVALIDAS));
        if(!encoder.matches(r.password(),u.getPasswordHash())) throw new AutenticacionException(Tipo.CREDENCIALES_INVALIDAS);
        Cliente c=u.getCliente();
        if(!u.isActivo() || !c.isActivo()) throw new AutenticacionException(Tipo.CUENTA_INACTIVA);
        String nombre=Stream.of(c.getPrimerNombre(),c.getSegundoNombre(),c.getApellidoPaterno(),c.getApellidoMaterno())
            .filter(Objects::nonNull).reduce((a,b) -> a+" "+b).orElse("");
        
        return new AuthResponse(tokens.emitir(u.getId().toString()),"Bearer",tokens.getExpirationMs(),u.getCorreo(),nombre);
    }
}
