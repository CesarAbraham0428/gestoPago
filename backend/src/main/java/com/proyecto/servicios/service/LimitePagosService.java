package com.proyecto.servicios.service;

import com.proyecto.servicios.service.exception.NegocioException;
import com.proyecto.servicios.utils.LimitadorVentanaFija;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/** Limita las solicitudes de pago por usuario (ventana fija de 60 s). */
@Service
public class LimitePagosService {

    private static final Duration VENTANA = Duration.ofMinutes(1);

    private final LimitadorVentanaFija limitador;
    private final int maximoPorVentana;

    public LimitePagosService(LimitadorVentanaFija limitador,
            @Value("${gestopago.pagos.limite-por-minuto:5}") int maximoPorVentana) {
        this.limitador = limitador;
        this.maximoPorVentana = maximoPorVentana;
    }

    public void verificar(Integer usuarioId) {
        if (limitador.excede("limite:pagos:" + usuarioId, maximoPorVentana, VENTANA)) {
            throw new NegocioException(HttpStatus.TOO_MANY_REQUESTS,
                    "Demasiadas solicitudes de pago; intenta de nuevo en un minuto");
        }
    }
}
