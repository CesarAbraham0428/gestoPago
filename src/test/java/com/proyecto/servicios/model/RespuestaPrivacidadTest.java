package com.proyecto.servicios.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.proyecto.servicios.entity.sf.*;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class RespuestaPrivacidadTest {
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

    @Test void detalleIncluyeDatosCompletosSinCredenciales() {
        Cliente cliente = new Cliente();
        cliente.setId(1);
        cliente.setEmpresa("Empresa privada");
        cliente.setOcupacion("Ingeniera");
        cliente.setIngresoMensual(new BigDecimal("12000.00"));
        Cuenta cuenta = new Cuenta();
        cuenta.setId(2); cuenta.setCliente(cliente); cuenta.setNumeroCuenta("000000000000000001");
        cuenta.setSaldo(new BigDecimal("999.00"));
        var json = mapper.valueToTree(ClienteMapper.cliente(cliente, new Domicilio(), usuario(cliente), List.of(cuenta)));
        for (String campo : new String[]{"password", "passwordHash"}) {
            assertFalse(json.has(campo), campo);
        }
        assertEquals(999.0, json.get("cuentas").get(0).get("saldo").asDouble());
        assertEquals(12000.0, json.get("ingresoMensual").asDouble());
        assertEquals("Empresa privada", json.get("empresa").asText());
        assertFalse(json.get("usuario").has("passwordHash"));
        assertEquals("000000000000000001", json.get("cuentas").get(0).get("numeroCuenta").asText());
    }

    private Usuario usuario(Cliente cliente) {
        Usuario u = new Usuario(); u.setCliente(cliente); u.setPasswordHash("no-exponer"); return u;
    }

    @Test void resumenDeClienteSoloExponeCamposNecesarios() {
        var json = mapper.valueToTree(new ClienteResumenResponse(1, "Ana", null, "Pérez", "Ruiz", true, null));
        assertEquals(7, json.size());
        for (String campo : new String[]{"curp", "rfc", "correoElectronico", "telefonoMovil", "fechaNacimiento", "domicilio", "cuentas"}) {
            assertFalse(json.has(campo), campo);
        }
    }
}
