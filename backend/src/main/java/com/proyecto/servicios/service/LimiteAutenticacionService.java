package com.proyecto.servicios.service;

import com.proyecto.servicios.service.exception.AutenticacionException;
import com.proyecto.servicios.service.exception.NegocioException;
import com.proyecto.servicios.utils.LimitadorVentanaFija;
import com.proyecto.servicios.utils.LimitadorVentanaFija.Resultado;
import java.time.Duration;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * Frena la fuerza bruta sobre las rutas públicas: el inicio de sesión se limita por correo (protege la
 * contraseña de una cuenta concreta) y por IP (protege contra barridos de muchas cuentas); el registro, por IP.
 * Al rechazar informa cuánto falta para reintentar (cabecera Retry-After).
 */
@Service
public class LimiteAutenticacionService {

    private static final Duration VENTANA_LOGIN = Duration.ofMinutes(1);
    private static final Duration VENTANA_REGISTRO = Duration.ofHours(1);
    private static final Duration VENTANA_DISPONIBILIDAD = Duration.ofMinutes(15);
    private static final String MENSAJE = "Demasiados intentos; espera antes de intentar de nuevo";

    private final LimitadorVentanaFija limitador;
    private final int maximoPorCorreo;
    private final int maximoPorIp;
    private final int maximoRegistrosPorIp;
    private final int maximoDisponibilidadPorIp;

    public LimiteAutenticacionService(LimitadorVentanaFija limitador,
            @Value("${gestopago.app.limites.login-por-correo:5}") int maximoPorCorreo,
            @Value("${gestopago.app.limites.login-por-ip:5}") int maximoPorIp,
            @Value("${gestopago.app.limites.registro-por-ip:10}") int maximoRegistrosPorIp,
            @Value("${gestopago.app.limites.disponibilidad-por-ip:60}") int maximoDisponibilidadPorIp) {
        this.limitador = limitador;
        this.maximoPorCorreo = maximoPorCorreo;
        this.maximoPorIp = maximoPorIp;
        this.maximoRegistrosPorIp = maximoRegistrosPorIp;
        this.maximoDisponibilidadPorIp = maximoDisponibilidadPorIp;
    }

    /**
     * Antes de /auth/login (por IP y correo) y /auth/verificar (solo por IP, pasando correo null): rechaza con 429 si
     * ya hubo {@code maximo} fallos en la ventana. No cuenta la solicitud; los fallos se anotan con
     * {@link #registrarFalloDeLogin}, así que un acceso correcto no suma.
     */
    public void verificarInicioDeSesion(String ip, String correo) {
        rechazarSiExcedido(limitador.consultar(claveIp(ip), maximoPorIp, VENTANA_LOGIN),
                correo == null ? Resultado.PERMITIDO
                        : limitador.consultar(claveCorreo(correo), maximoPorCorreo, VENTANA_LOGIN));
    }

    /** Anota un fallo si {@code causa} es contraseña o código incorrectos; cualquier otro error no cuenta. */
    public void registrarFalloDeLogin(String ip, String correo, RuntimeException causa) {
        // Una cuenta inactiva, un 409 o un 429 no son intentos de adivinar credenciales.
        boolean credencialesIncorrectas = causa instanceof AutenticacionException a
                && a.getTipo() == AutenticacionException.Tipo.CREDENCIALES_INVALIDAS
                || causa instanceof NegocioException n && n.getStatus() == HttpStatus.UNAUTHORIZED;
        if (!credencialesIncorrectas) {
            return;
        }
        limitador.registrar(claveIp(ip), maximoPorIp, VENTANA_LOGIN);
        if (correo != null) {
            limitador.registrar(claveCorreo(correo), maximoPorCorreo, VENTANA_LOGIN);
        }
    }

    private static String claveIp(String ip) {
        return "limite:login:ip:" + ip;
    }

    private static String claveCorreo(String correo) {
        return "limite:login:correo:" + correo.strip().toLowerCase(Locale.ROOT);
    }

    public void verificarRegistro(String ip) {
        rechazarSiExcedido(limitador.registrar("limite:registro:ip:" + ip, maximoRegistrosPorIp, VENTANA_REGISTRO));
    }

    /** Frena el barrido de correos, CURP, RFC o teléfonos para averiguar cuáles ya tienen cuenta. */
    public void verificarDisponibilidad(String ip) {
        rechazarSiExcedido(limitador.registrar("limite:disponibilidad:ip:" + ip, maximoDisponibilidadPorIp,
                VENTANA_DISPONIBILIDAD));
    }

    private static void rechazarSiExcedido(Resultado... resultados) {
        long espera = 0;
        boolean excedido = false;
        for (Resultado resultado : resultados) {
            if (resultado.excedido()) {
                excedido = true;
                espera = Math.max(espera, resultado.reintentarEnSegundos());
            }
        }
        if (excedido) {
            throw new NegocioException(HttpStatus.TOO_MANY_REQUESTS, MENSAJE, espera);
        }
    }
}
