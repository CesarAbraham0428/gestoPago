package com.proyecto.servicios.security;

import static org.springframework.security.web.util.matcher.AntPathRequestMatcher.antMatcher;

import com.proyecto.servicios.service.SesionService;
import com.proyecto.servicios.utils.LimitadorVentanaFija;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.DelegatingRequestMatcherHeaderWriter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;
import org.springframework.security.web.header.writers.StaticHeadersWriter;
import org.springframework.security.web.util.matcher.NegatedRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableWebSecurity
@Slf4j
public class SecurityConfiguration {

    /** Únicas rutas POST sin sesión: registro y los dos pasos del inicio de sesión. */
    private static final List<String> POST_PUBLICOS = List.of("/clientes", "/auth/login", "/auth/verificar");

    /** La API solo sirve JSON: nada del navegador debe poder cargar recursos de ella. Swagger necesita más. */
    private static final String CSP_API = "default-src 'none'; frame-ancestors 'none'; base-uri 'none'";

    static boolean esRutaPublica(String metodo, String ruta) {
        return "POST".equals(metodo) && POST_PUBLICOS.contains(ruta);
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http,
            @Value("${gestopago.app.cors.allowed-origin-patterns:http://localhost:*,http://127.0.0.1:*}")
            List<String> origenesPermitidos, JwtTokenService tokenService,
            SesionService sesiones, ApiSecurityErrorWriter errores, LimitadorVentanaFija limitador,
            @Value("${gestopago.app.limites.global-por-ip:120}") int globalPorIp,
            @Value("${gestopago.app.limites.global-por-usuario:120}") int globalPorUsuario,
            @Value("${gestopago.app.limites.rafaga-publica-por-ip:20}") int rafagaPublicaPorIp,
            @Value("${gestopago.app.limites.tamano-maximo-bytes:65536}") long tamanoMaximoBytes) throws Exception {
        JwtAuthenticationFilter jwtFilter = new JwtAuthenticationFilter(tokenService, sesiones, errores);
        // Los filtros no son beans: así Spring Boot no los registra además como filtros del contenedor.
        LimiteSolicitudesPorIpFilter limitePorIp =
                new LimiteSolicitudesPorIpFilter(limitador, errores, globalPorIp, rafagaPublicaPorIp);
        LimiteSolicitudesPorUsuarioFilter limitePorUsuario =
                new LimiteSolicitudesPorUsuarioFilter(limitador, errores, globalPorUsuario);
        TamanoSolicitudFilter tamano = new TamanoSolicitudFilter(tamanoMaximoBytes, errores);
        return http
                // API sin estado: el JWT viaja solo en el encabezado Authorization (nunca en cookies), por lo que
                // no hay credencial ambiental que un sitio ajeno pueda reutilizar y CSRF no aplica.
                .csrf(csrf -> csrf.disable()) // NOSONAR java:S4502
                .headers(headers -> headers
                        .referrerPolicy(referrer -> referrer.policy(ReferrerPolicy.NO_REFERRER))
                        .frameOptions(frame -> frame.deny())
                        // Solo se emite sobre HTTPS (detrás del proxy, con FORWARD_HEADERS=native).
                        .httpStrictTransportSecurity(hsts -> hsts.maxAgeInSeconds(31_536_000).includeSubDomains(true))
                        .addHeaderWriter(new DelegatingRequestMatcherHeaderWriter(
                                new NegatedRequestMatcher(new OrRequestMatcher(
                                        antMatcher("/swagger-ui/**"), antMatcher("/v3/api-docs/**"))),
                                new StaticHeadersWriter("Content-Security-Policy", CSP_API)))
                        .addHeaderWriter(new StaticHeadersWriter("Permissions-Policy",
                                "geolocation=(), microphone=(), camera=(), payment=(), usb=()"))
                        .addHeaderWriter(new StaticHeadersWriter("Cross-Origin-Opener-Policy", "same-origin")))
                .cors(cors -> cors.configurationSource(corsConfigurationSource(origenesPermitidos)))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(errors -> errors.authenticationEntryPoint((request, response, exception) -> {
                    log.warn("Solicitud protegida sin autenticación válida: {} {}",
                            request.getMethod(), request.getRequestURI());
                    errores.escribir(response, HttpStatus.UNAUTHORIZED, "Se requiere iniciar sesión");
                }).accessDeniedHandler((request, response, exception) ->
                        errores.escribir(response, HttpStatus.FORBIDDEN,
                                "No tienes permiso para realizar esta operación")))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers(HttpMethod.POST, POST_PUBLICOS.toArray(String[]::new)).permitAll()
                        // Sonda de salud de la plataforma (Render); solo devuelve {"status":"UP"} sin detalles.
                        .requestMatchers(HttpMethod.GET, "/actuator/health").permitAll()
                        .requestMatchers("/error", "/swagger-ui/**", "/v3/api-docs/**").permitAll()
                        .anyRequest().authenticated())
                // Orden: CORS -> tamaño -> límite por IP -> JWT -> límite por usuario. Así un 429 o 413 también
                // lleva las cabeceras CORS y el navegador puede leer Retry-After.
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(limitePorIp, JwtAuthenticationFilter.class)
                .addFilterBefore(tamano, LimiteSolicitudesPorIpFilter.class)
                .addFilterAfter(limitePorUsuario, JwtAuthenticationFilter.class)
                .build();
    }

    /** Permite que la app web (otro origen) llame a la API; los orígenes se configuran por propiedad. */
    static CorsConfigurationSource corsConfigurationSource(List<String> origenes) {
        for (String origen : origenes) {
            String limpio = origen.strip();
            if ("*".equals(limpio) || "http://*".equals(limpio) || "https://*".equals(limpio)) {
                throw new IllegalStateException("CORS_ORIGENES no puede permitir cualquier origen (" + limpio
                        + "); usa la URL exacta del frontend");
            }
        }
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(origenes);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        config.setExposedHeaders(List.of("Retry-After"));
        config.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }
}
