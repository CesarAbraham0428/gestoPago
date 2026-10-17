package com.proyecto.servicios.models.response;

import java.math.BigDecimal;
import java.time.Instant;

public record CuentaResponse(Integer id, Integer clienteId, String numeroCuenta, BigDecimal saldo,
    boolean estaActiva, Instant fechaCreacion, Instant fechaActualizacion) {}
