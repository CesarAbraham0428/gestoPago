package com.proyecto.servicios.integration;

import com.proyecto.servicios.service.EnviadorCodigo;
import java.time.Duration;
import org.springframework.stereotype.Component;

/** Sustituye al correo en las pruebas de integración: conserva el último código "enviado". */
@Component
public class EnviadorCodigoDePrueba implements EnviadorCodigo {
    private volatile String ultimoCodigo;

    @Override
    public void enviar(String correo, String codigo, Duration vigencia) {
        ultimoCodigo = codigo;
    }

    public String ultimoCodigo() {
        return ultimoCodigo;
    }
}
