package com.proyecto.servicios.service;

import com.proyecto.servicios.service.exception.NegocioException;
import com.proyecto.servicios.utils.LimitadorVentanaFija;
import java.time.Duration;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * Frena la fuerza bruta sobre las rutas públicas: el inicio de sesión se limita por correo (protege la
 * contraseña de una cuenta concreta) y por IP (protege contra barridos de muchas cuentas); el registro, por IP.
 */
@Service
public class LimiteAutenticacionService {

    private static final Duration VENTANA_LOGIN = Duration.ofMinutes(15);
    private static final Duration VENTANA_REGISTRO = Duration.ofHours(1);
    private static final String MENSAJE = "Demasiados intentos; espera unos minutos antes de intentar de nuevo";

    private final LimitadorVentanaFija limitador;
    private final int maximoPorCorreo;
    private final int maximoPorIp;
    private final int maximoRegistrosPorIp;

    public LimiteAutenticacionService(LimitadorVentanaFija limitador,
            @Value("${gestopago.app.limites.login-por-correo:10}") int maximoPorCorreo,
            @Value("${gestopago.app.limites.login-por-ip:40}") int maximoPorIp,
            @Value("${gestopago.app.limites.registro-por-ip:10}") int maximoRegistrosPorIp) {
        this.limitador = limitador;
        this.maximoPorCorreo = maximoPorCorreo;
        this.maximoPorIp = maximoPorIp;
        this.maximoRegistrosPorIp = maximoRegistrosPorIp;
    }

    /** Para /auth/login (por IP y correo) y /auth/verificar (solo por IP, pasando correo null). */
    public void verificarInicioDeSesion(String ip, String correo) {
        boolean excedido = limitador.excede("limite:login:ip:" + ip, maximoPorIp, VENTANA_LOGIN);
        if (correo != null) {
            excedido |= limitador.excede("limite:login:correo:" + correo.strip().toLowerCase(Locale.ROOT),
                    maximoPorCorreo, VENTANA_LOGIN);
        }
        if (excedido) {
            throw new NegocioException(HttpStatus.TOO_MANY_REQUESTS, MENSAJE);
        }
    }

    public void verificarRegistro(String ip) {
        if (limitador.excede("limite:registro:ip:" + ip, maximoRegistrosPorIp, VENTANA_REGISTRO)) {
            throw new NegocioException(HttpStatus.TOO_MANY_REQUESTS, MENSAJE);
        }
    }
}
