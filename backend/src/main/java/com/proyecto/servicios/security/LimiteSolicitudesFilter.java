package com.proyecto.servicios.security;

import com.proyecto.servicios.utils.LimitadorVentanaFija;
import com.proyecto.servicios.utils.LimitadorVentanaFija.Resultado;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Límite general de solicitudes por minuto sobre TODAS las rutas, antes de llegar a los controladores. Frena
 * inundaciones y barridos; los límites específicos del login, el registro y los pagos siguen aplicándose además.
 * Las subclases deciden por qué se cuenta (IP o usuario autenticado).
 */
@Slf4j
abstract class LimiteSolicitudesFilter extends OncePerRequestFilter {

    private static final Duration VENTANA = Duration.ofMinutes(1);
    private static final String RUTA_SALUD = "/actuator/health";

    private final LimitadorVentanaFija limitador;
    private final ApiSecurityErrorWriter errores;

    LimiteSolicitudesFilter(LimitadorVentanaFija limitador, ApiSecurityErrorWriter errores) {
        this.limitador = limitador;
        this.errores = errores;
    }

    /** Resultado de contar esta solicitud; {@link Resultado#PERMITIDO} si no aplica. */
    abstract Resultado contar(HttpServletRequest request, LimitadorVentanaFija limitador, Duration ventana);

    /** Identificación del origen, solo para el registro de bloqueos. */
    abstract String origen(HttpServletRequest request);

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // Las preflight de CORS y la sonda de salud de la plataforma no cuentan.
        return "OPTIONS".equals(request.getMethod()) || RUTA_SALUD.equals(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Resultado resultado = contar(request, limitador, VENTANA);
        if (resultado.excedido()) {
            log.warn("Límite de solicitudes superado: {} {} {}", origen(request), request.getMethod(),
                    request.getRequestURI());
            response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(resultado.reintentarEnSegundos()));
            errores.escribir(response, HttpStatus.TOO_MANY_REQUESTS,
                    "Demasiadas solicitudes; espera un momento antes de intentar de nuevo");
            return;
        }
        chain.doFilter(request, response);
    }
}
