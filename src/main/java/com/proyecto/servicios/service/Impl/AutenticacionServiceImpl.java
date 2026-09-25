package com.proyecto.servicios.service.Impl;

import com.proyecto.servicios.entity.sf.Personas;
import com.proyecto.servicios.entity.sf.Registro;
import com.proyecto.servicios.model.AuthResponse;
import com.proyecto.servicios.model.LoginRequest;
import com.proyecto.servicios.model.RegistroRequest;
import com.proyecto.servicios.repositorys.sf.PersonasRepository;
import com.proyecto.servicios.repositorys.sf.RegistroRepository;
import com.proyecto.servicios.security.JwtTokenService;
import com.proyecto.servicios.service.AutenticacionService;
import com.proyecto.servicios.service.exception.AutenticacionException;
import com.proyecto.servicios.service.exception.AutenticacionException.Tipo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
@Slf4j
public class AutenticacionServiceImpl implements AutenticacionService {
    private final RegistroRepository registroRepository;
    private final PersonasRepository personasRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService jwtTokenService;

    public AutenticacionServiceImpl(RegistroRepository registroRepository, PersonasRepository personasRepository,
                                    PasswordEncoder passwordEncoder, JwtTokenService jwtTokenService) {
        this.registroRepository = registroRepository;
        this.personasRepository = personasRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenService = jwtTokenService;
    }

    @Override
    @Transactional
    public AuthResponse registrar(RegistroRequest request) {
        String usuario = request.usuario().trim().toLowerCase(Locale.ROOT);
        if (registroRepository.existsByUsuarioIgnoreCase(usuario)) {
            log.warn("Se rechazó un registro por usuario duplicado");
            throw new AutenticacionException(Tipo.USUARIO_DUPLICADO);
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
            log.warn("Se rechazó un registro por una restricción de unicidad");
            throw new AutenticacionException(Tipo.USUARIO_DUPLICADO);
        }
        log.info("Cuenta registrada correctamente");
        return crearRespuesta(registro);
    }

    @Override
    @Transactional(readOnly = true)
    public AuthResponse iniciarSesion(LoginRequest request) {
        Registro registro = registroRepository.findByUsuarioIgnoreCase(request.usuario().trim())
                .orElseThrow(() -> {
                    log.warn("Intento de inicio de sesión con credenciales inválidas");
                    return new AutenticacionException(Tipo.CREDENCIALES_INVALIDAS);
                });
        if (!registro.isActivo()) {
            log.warn("Se rechazó el inicio de sesión de una cuenta inactiva");
            throw new AutenticacionException(Tipo.CUENTA_INACTIVA);
        }
        if (!passwordEncoder.matches(request.password(), registro.getPasswordHash())) {
            log.warn("Intento de inicio de sesión con credenciales inválidas");
            throw new AutenticacionException(Tipo.CREDENCIALES_INVALIDAS);
        }
        registro.getPersona().getNombre();
        log.info("Inicio de sesión correcto");
        return crearRespuesta(registro);
    }

    private AuthResponse crearRespuesta(Registro registro) {
        Personas persona = registro.getPersona();
        String nombreCompleto = String.join(" ", persona.getNombre(), persona.getApellidoP(), persona.getApellidoMaterno())
                .trim().replaceAll("\\s+", " ");
        return new AuthResponse(jwtTokenService.emitir(registro.getUsuario()), "Bearer",
                jwtTokenService.getExpirationMs(), registro.getUsuario(), nombreCompleto);
    }
}
