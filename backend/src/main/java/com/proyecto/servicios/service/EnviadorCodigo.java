package com.proyecto.servicios.service;

import java.time.Duration;

/** Puerto de salida: entrega al usuario el código de verificación del segundo factor. */
public interface EnviadorCodigo {

    /** @throws RuntimeException si no se pudo entregar el código (el desafío no debe quedar vigente). */
    void enviar(String correo, String codigo, Duration vigencia);
}
