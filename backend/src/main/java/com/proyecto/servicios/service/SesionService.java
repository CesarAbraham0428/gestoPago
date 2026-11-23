package com.proyecto.servicios.service;

import com.proyecto.servicios.repositorys.sf.SesionUsuarioRepository;
import com.proyecto.servicios.repositorys.sf.UsuarioRepository;
import com.proyecto.servicios.security.JwtTokenService.TokenEmitido;
import java.time.Clock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Registro central de sesiones: un usuario tiene a lo sumo una sesión activa. El JWT por sí solo no basta;
 * además debe coincidir con la sesión guardada, de modo que cerrar sesión o iniciarla en otro dispositivo
 * invalida los tokens anteriores de inmediato.
 */
@Service
public class SesionService {

    public enum Estado { VALIDA, USUARIO_INACTIVO, REEMPLAZADA, FINALIZADA }

    private final SesionUsuarioRepository sesiones;
    private final UsuarioRepository usuarios;
    private final Clock clock;

    @Autowired
    public SesionService(SesionUsuarioRepository sesiones, UsuarioRepository usuarios) {
        this(sesiones, usuarios, Clock.systemUTC());
    }

    SesionService(SesionUsuarioRepository sesiones, UsuarioRepository usuarios, Clock clock) {
        this.sesiones = sesiones;
        this.usuarios = usuarios;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Estado estado(Integer usuarioId, String jti) {
        var vigente = sesiones.jtiVigente(usuarioId, clock.instant());
        if (vigente.filter(actual -> actual.equals(jti)).isPresent()) {
            return Estado.VALIDA;
        }
        if (!usuarios.existsByIdAndActivoTrueAndClienteActivoTrue(usuarioId)) {
            return Estado.USUARIO_INACTIVO;
        }
        return vigente.isPresent() ? Estado.REEMPLAZADA : Estado.FINALIZADA;
    }

    /** @return false si ya hay una sesión vigente y {@code reemplazar} es falso. */
    @Transactional
    public boolean abrir(Integer usuarioId, TokenEmitido token, boolean reemplazar) {
        return sesiones.abrir(usuarioId, token.jti(), clock.instant(), token.expiraEn(), reemplazar) > 0;
    }

    @Transactional
    public void cerrar(Integer usuarioId) {
        sesiones.cerrar(usuarioId);
    }
}
