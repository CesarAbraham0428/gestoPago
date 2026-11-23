package com.proyecto.servicios.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Rechaza cuerpos declarados más grandes de lo que la API necesita (todas sus peticiones son JSON pequeño), antes
 * de leerlos. Frena el consumo de memoria y de ancho de banda por peticiones infladas.
 */
@Slf4j
class TamanoSolicitudFilter extends OncePerRequestFilter {

    private final long maximoBytes;
    private final ApiSecurityErrorWriter errores;

    TamanoSolicitudFilter(long maximoBytes, ApiSecurityErrorWriter errores) {
        this.maximoBytes = maximoBytes;
        this.errores = errores;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (request.getContentLengthLong() > maximoBytes) {
            log.warn("Cuerpo demasiado grande rechazado: {} bytes en {} {}", request.getContentLengthLong(),
                    request.getMethod(), request.getRequestURI());
            errores.escribir(response, HttpStatus.PAYLOAD_TOO_LARGE, "La solicitud es demasiado grande");
            return;
        }
        chain.doFilter(request, response);
    }
}
