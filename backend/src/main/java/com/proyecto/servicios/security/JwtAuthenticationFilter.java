package com.proyecto.servicios.security;

import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.proyecto.servicios.service.SesionService;
import com.proyecto.servicios.service.SesionService.Estado;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.TransactionException;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Autentica cada solicitud con el JWT y comprueba contra el servidor que el token sea el de la sesión vigente
 * del usuario (sesión única, cierre de sesión y usuario activo). Si no se puede consultar la sesión, la
 * solicitud se rechaza: nunca se autentica "a ciegas".
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Map<Estado, String> MENSAJES_DE_RECHAZO = Map.of(
            Estado.USUARIO_INACTIVO, "Usuario inactivo o inexistente",
            Estado.REEMPLAZADA, "Tu sesión se cerró porque iniciaste sesión en otro dispositivo",
            Estado.FINALIZADA, "Tu sesión ya no está activa; inicia sesión de nuevo");

    private final JwtTokenService tokens;
    private final SesionService sesiones;
    private final ApiSecurityErrorWriter errores;

    public JwtAuthenticationFilter(JwtTokenService tokens, SesionService sesiones, ApiSecurityErrorWriter errores) {
        this.tokens = tokens;
        this.sesiones = sesiones;
        this.errores = errores;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return SecurityConfiguration.esRutaPublica(request.getMethod(), request.getServletPath());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            try {
                DecodedJWT jwt = tokens.validar(header.substring(7));
                Integer usuarioId = Integer.valueOf(jwt.getSubject());
                Estado estado = sesiones.estado(usuarioId, jwt.getId());
                if (estado != Estado.VALIDA) {
                    rechazar(response, MENSAJES_DE_RECHAZO.get(estado));
                    return;
                }
                SecurityContextHolder.getContext().setAuthentication(
                        new UsernamePasswordAuthenticationToken(usuarioId.toString(), null,
                                List.of(new SimpleGrantedAuthority("ROLE_USER"))));
            } catch (JWTVerificationException | IllegalArgumentException ex) {
                rechazar(response, "Tu sesión expiró o no es válida; inicia sesión de nuevo");
                return;
            } catch (DataAccessException | TransactionException ex) {
                SecurityContextHolder.clearContext();
                errores.escribir(response, HttpStatus.INTERNAL_SERVER_ERROR,
                        "No fue posible verificar la sesión; intenta nuevamente");
                return;
            }
        }
        chain.doFilter(request, response);
    }

    private void rechazar(HttpServletResponse response, String mensaje) throws IOException {
        SecurityContextHolder.clearContext();
        errores.escribir(response, HttpStatus.UNAUTHORIZED, mensaje);
    }
}
