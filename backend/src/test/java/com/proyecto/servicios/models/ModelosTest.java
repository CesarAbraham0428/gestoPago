package com.proyecto.servicios.models;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.proyecto.servicios.entity.sf.SesionUsuario;
import com.proyecto.servicios.models.request.ClienteActualizacionPorIdentidadRequest;
import com.proyecto.servicios.models.request.ClienteConsultaRequest;
import com.proyecto.servicios.models.request.ClienteIdentificacionRequest;
import com.proyecto.servicios.models.request.ClienteRequest;
import com.proyecto.servicios.models.request.CuentaPinRequest;
import com.proyecto.servicios.models.request.DomicilioRequest;
import com.proyecto.servicios.models.request.LoginRequest;
import com.proyecto.servicios.models.request.PagoServicioRequest;
import com.proyecto.servicios.models.request.VerificacionCodigoRequest;
import com.proyecto.servicios.models.response.DesafioResponse;
import com.proyecto.servicios.models.response.HistorialPagoResponse;
import com.proyecto.servicios.models.response.gestopago.GestoPagoPagosResponse;
import com.proyecto.servicios.service.exception.ClienteYaRegistradoException;
import com.proyecto.servicios.service.exception.CurpDuplicadaException;
import com.proyecto.servicios.service.exception.RfcDuplicadoException;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class ModelosTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void laPeticionesRechazanCamposDesconocidosYNoRevelanSecretosEnToString() {
        assertThrows(IllegalArgumentException.class, () -> new CuentaPinRequest("1", "1234", "5678").rechazarCampo("x", 1));
        assertThrows(IllegalArgumentException.class, () -> new LoginRequest("a@b.mx", "secreto").rechazarCampo("x", 1));
        assertThrows(IllegalArgumentException.class,
                () -> new VerificacionCodigoRequest("d", "123456", false).rechazarCampo("x", 1));
        assertThrows(IllegalArgumentException.class,
                () -> new PagoServicioRequest("1", 1, 1, BigDecimal.ONE, null, null, "1234").rechazarCampo("x", 1));
        assertThrows(IllegalArgumentException.class,
                () -> new DomicilioRequest("c", "1", null, "c", "m", "e", "12345", "p").rechazarCampo("x", 1));
        assertThrows(IllegalArgumentException.class,
                () -> new ClienteIdentificacionRequest(null, null).rechazarCampo("x", 1));
        assertThrows(IllegalArgumentException.class,
                () -> new ClienteActualizacionPorIdentidadRequest(null, null).rechazarCampo("x", 1));
        assertThrows(IllegalArgumentException.class, () -> new ClienteConsultaRequest().rechazarCampo("x", 1));
        assertThrows(IllegalArgumentException.class, () -> clienteRequest().rechazarCampo("x", 1));

        assertFalse(new CuentaPinRequest("123", "1234", "5678").toString().contains("5678"));
        assertFalse(new LoginRequest("a@b.mx", "secreto").toString().contains("secreto"));
        assertFalse(new DesafioResponse.Enrolamiento("SECRETOBASE32", "otpauth://totp/x?secret=SECRETOBASE32")
                .toString().contains("SECRETOBASE32"));
        assertFalse(new VerificacionCodigoRequest("d", "123456", true).toString().contains("123456"));
        assertFalse(new PagoServicioRequest("1", 1, 2, BigDecimal.ONE, null, null, "9876").toString()
                .contains("9876"));
    }

    private ClienteRequest clienteRequest() {
        return new ClienteRequest("A", null, "B", "C", LocalDate.of(1990, 1, 1), "C", "R", null, "N", null,
                "a@b.mx", "5555555555", null, "O", "E", BigDecimal.ONE, null, "P");
    }

    @Test
    void lasFechasDeUnaConsultaDeClientesSeValidan() {
        ClienteConsultaRequest request = new ClienteConsultaRequest();
        assertTrue(request.isRangoFechasValido());
        assertTrue(request.isFechasEnRango());

        request.setDesde(LocalDate.of(2026, 5, 1));
        assertTrue(request.isRangoFechasValido());
        request.setHasta(LocalDate.of(2026, 4, 1));
        assertFalse(request.isRangoFechasValido());
        request.setHasta(LocalDate.of(2026, 6, 1));
        assertTrue(request.isRangoFechasValido());

        request.setDesde(LocalDate.of(0, 1, 1));
        assertFalse(request.isFechasEnRango());
        request.setDesde(LocalDate.of(2026, 1, 1));
        request.setHasta(LocalDate.of(10000, 1, 1));
        assertFalse(request.isFechasEnRango());
    }

    @Test
    void laIdentificacionExigeRfcOCurp() {
        assertFalse(new ClienteIdentificacionRequest(null, null).isIdentificacionPresente());
        assertTrue(new ClienteIdentificacionRequest("R", null).isIdentificacionPresente());
        assertTrue(new ClienteIdentificacionRequest(null, "C").isIdentificacionPresente());
    }

    @Test
    void losCambiosDeUnPatchDebenSerUnObjetoNoVacio() throws Exception {
        JsonNode objeto = mapper.readTree("{\"a\":1}");
        assertTrue(new ClienteActualizacionPorIdentidadRequest(null, null).isCambiosValidos());
        assertTrue(new ClienteActualizacionPorIdentidadRequest(null, objeto).isCambiosValidos());
        assertFalse(new ClienteActualizacionPorIdentidadRequest(null, mapper.readTree("{}")).isCambiosValidos());
        assertFalse(new ClienteActualizacionPorIdentidadRequest(null, mapper.readTree("[1]")).isCambiosValidos());
    }

    @Test
    void elCodigoDeGestoPagoSeComparaComoEntero() {
        GestoPagoPagosResponse respuesta = new GestoPagoPagosResponse();
        assertFalse(respuesta.esCodigo("01"));

        respuesta.setCodigo("1");
        assertTrue(respuesta.esExitoso());
        respuesta.setCodigo(" 02 ");
        assertTrue(respuesta.esSaldoInsuficiente());
        respuesta.setCodigo("abc");
        assertFalse(respuesta.esCodigo("01"));
    }

    @Test
    void lasExcepcionesDeDuplicadoResponden409() {
        assertEquals(HttpStatus.CONFLICT, new ClienteYaRegistradoException().getStatus());
        assertEquals("CURP duplicada", new CurpDuplicadaException().getMessage());
        assertEquals("RFC duplicado", new RfcDuplicadoException().getMessage());
    }

    @Test
    void elHistorialOmiteLosCamposNulos() throws Exception {
        HistorialPagoResponse pago = new HistorialPagoResponse("upc", "APROBADO", null, null, 1, 2,
                BigDecimal.TEN, BigDecimal.ONE, BigDecimal.valueOf(11), null, null, null, null, null);

        JsonNode json = mapper.findAndRegisterModules().valueToTree(pago);

        assertEquals("upc", json.get("upc").asText());
        assertFalse(json.has("producto"));
    }

    @Test
    void laSesionDeUsuarioSeCreaVacia() {
        assertNotNull(new SesionUsuario());
    }
}
