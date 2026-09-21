package com.proyecto.servicios.service;

import com.proyecto.servicios.entity.sf.Personas;
import com.proyecto.servicios.entity.sf.Registro;
import com.proyecto.servicios.model.AuthResponse;
import com.proyecto.servicios.model.LoginRequest;
import com.proyecto.servicios.model.RegistroRequest;
import com.proyecto.servicios.repositorys.sf.PersonasRepository;
import com.proyecto.servicios.repositorys.sf.RegistroRepository;
import com.proyecto.servicios.security.JwtTokenService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
public class AutenticacionService {
    private final RegistroRepository registroRepository;
    private final PersonasRepository personasRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService jwtTokenService;

    public AutenticacionService(RegistroRepository registroRepository, PersonasRepository personasRepository,
                                PasswordEncoder passwordEncoder, JwtTokenService jwtTokenService) {
        this.registroRepository = registroRepository;
        this.personasRepository = personasRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenService = jwtTokenService;
    }

    @Transactional
    public AuthResponse registrar(RegistroRequest request) {
        String usuario = request.usuario().trim().toLowerCase(Locale.ROOT);
        if (registroRepository.existsByUsuarioIgnoreCase(usuario)) {
            throw new UsuarioDuplicadoException();
        }

        Personas persona = new Personas();
        persona.setNombre(request.nombre().trim());
        persona.setApellidoP(request.apellidoPaterno().trim());
        persona.setApellidoMaterno(request.apellidoMaterno().trim());
        persona.setCorreo(request.correo().trim().toLowerCase(Locale.ROOT));
        persona.setTelefono(request.telefono().trim());
        personasRepository.save(persona);

        Registro registro = new Registro();
        registro.setUsuario(usuario);
        registro.setPasswordHash(passwordEncoder.encode(request.password()));
        registro.setActivo(true);
        registro.setPersona(persona);
        try {
            registroRepository.save(registro);
        } catch (DataIntegrityViolationException exception) {
            throw new UsuarioDuplicadoException();
        }
        return crearRespuesta(registro);
    }

    @Transactional(readOnly = true)
    public AuthResponse iniciarSesion(LoginRequest request) {
        Registro registro = registroRepository.findByUsuarioIgnoreCase(request.usuario().trim())
                .orElseThrow(CredencialesInvalidasException::new);
        if (!registro.isActivo()) {
            throw new CuentaInactivaException();
        }
        if (!passwordEncoder.matches(request.password(), registro.getPasswordHash())) {
            throw new CredencialesInvalidasException();
        }
        registro.getPersona().getNombre();
        return crearRespuesta(registro);
    }

    private AuthResponse crearRespuesta(Registro registro) {
        Personas persona = registro.getPersona();
        String nombreCompleto = String.join(" ", persona.getNombre(), persona.getApellidoP(), persona.getApellidoMaterno())
                .trim().replaceAll("\\s+", " ");
        return new AuthResponse(jwtTokenService.emitir(registro.getUsuario()), "Bearer",
                jwtTokenService.getExpirationMs(), registro.getUsuario(), nombreCompleto);
    }

    public static class UsuarioDuplicadoException extends RuntimeException {}
    public static class CredencialesInvalidasException extends RuntimeException {}
    public static class CuentaInactivaException extends RuntimeException {}
}
