package com.proyecto.servicios.security;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.proyecto.servicios.repositorys.sf.UsuarioRepository;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.util.List;
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private final JwtTokenService tokens;
    private final UsuarioRepository usuarios;
    public JwtAuthenticationFilter(JwtTokenService tokens,UsuarioRepository usuarios) {
        this.tokens=tokens; this.usuarios=usuarios;
    }
    @Override protected boolean shouldNotFilter(HttpServletRequest request) {
        return "POST".equals(request.getMethod()) &&
            ("/clientes".equals(request.getServletPath()) || "/auth/login".equals(request.getServletPath()));
    }
    @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain)
        throws ServletException,IOException {
        String header=request.getHeader("Authorization");
        if(header!=null && header.startsWith("Bearer ")) {
            try {
                Integer id=Integer.valueOf(tokens.validar(header.substring(7)).getSubject());
                if(!usuarios.existsByIdAndActivoTrueAndClienteActivoTrue(id)) {
                    rechazar(response,"Usuario inactivo o inexistente"); return;
                }
                SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                    id.toString(),null,List.of(new SimpleGrantedAuthority("ROLE_USER"))));
            } catch(JWTVerificationException | IllegalArgumentException ex) {
                rechazar(response,"Token inválido o vencido"); return;
            }
        }
        chain.doFilter(request,response);
    }
    private void rechazar(HttpServletResponse response,String mensaje) throws IOException {
        SecurityContextHolder.clearContext(); response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE); response.setCharacterEncoding("UTF-8");
        response.getWriter().write("{\"codigo\":1,\"mensaje\":\""+mensaje+"\"}");
    }
}
