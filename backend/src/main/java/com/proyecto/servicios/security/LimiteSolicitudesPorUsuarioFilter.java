package com.proyecto.servicios.security;

import com.proyecto.servicios.utils.LimitadorVentanaFija;
import com.proyecto.servicios.utils.LimitadorVentanaFija.Resultado;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Cuenta por usuario autenticado (se ejecuta después del filtro JWT). Evita que una sesión robada o un cliente
 * descontrolado sature la API aunque rote de IP. Las solicitudes sin sesión no se cuentan aquí.
 */
class LimiteSolicitudesPorUsuarioFilter extends LimiteSolicitudesFilter {

    private final int maximoPorMinuto;

    LimiteSolicitudesPorUsuarioFilter(LimitadorVentanaFija limitador, ApiSecurityErrorWriter errores,
            int maximoPorMinuto) {
        super(limitador, errores);
        this.maximoPorMinuto = maximoPorMinuto;
    }

    @Override
    Resultado contar(HttpServletRequest request, LimitadorVentanaFija limitador, Duration ventana) {
        Authentication autenticacion = SecurityContextHolder.getContext().getAuthentication();
        if (autenticacion == null || autenticacion instanceof AnonymousAuthenticationToken
                || !autenticacion.isAuthenticated()) {
            return Resultado.PERMITIDO;
        }
        return limitador.registrar("limite:global:usuario:" + autenticacion.getName(), maximoPorMinuto, ventana);
    }

    @Override
    String origen(HttpServletRequest request) {
        Authentication autenticacion = SecurityContextHolder.getContext().getAuthentication();
        return "usuario " + (autenticacion == null ? "?" : autenticacion.getName());
    }
}
