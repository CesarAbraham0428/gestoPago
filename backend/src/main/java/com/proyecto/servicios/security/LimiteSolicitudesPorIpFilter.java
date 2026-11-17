package com.proyecto.servicios.security;

import com.proyecto.servicios.utils.DireccionCliente;
import com.proyecto.servicios.utils.LimitadorVentanaFija;
import com.proyecto.servicios.utils.LimitadorVentanaFija.Resultado;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;

/**
 * Cuenta por IP cualquier solicitud, con sesión o sin ella. Las rutas públicas (registro y login) tienen además un
 * límite de ráfaga más estricto, porque son las que se atacan por fuerza bruta.
 */
class LimiteSolicitudesPorIpFilter extends LimiteSolicitudesFilter {

    private final int maximoPorMinuto;
    private final int maximoRafagaPublica;

    LimiteSolicitudesPorIpFilter(LimitadorVentanaFija limitador, ApiSecurityErrorWriter errores,
            int maximoPorMinuto, int maximoRafagaPublica) {
        super(limitador, errores);
        this.maximoPorMinuto = maximoPorMinuto;
        this.maximoRafagaPublica = maximoRafagaPublica;
    }

    @Override
    Resultado contar(HttpServletRequest request, LimitadorVentanaFija limitador, Duration ventana) {
        String ip = DireccionCliente.de(request);
        Resultado general = limitador.registrar("limite:global:ip:" + ip, maximoPorMinuto, ventana);
        if (!SecurityConfiguration.esRutaPublica(request.getMethod(), request.getRequestURI())) {
            return general;
        }
        Resultado rafaga = limitador.registrar("limite:rafaga:ip:" + ip, maximoRafagaPublica, ventana);
        return new Resultado(general.excedido() || rafaga.excedido(),
                Math.max(general.reintentarEnSegundos(), rafaga.reintentarEnSegundos()));
    }

    @Override
    String origen(HttpServletRequest request) {
        return DireccionCliente.de(request);
    }
}
