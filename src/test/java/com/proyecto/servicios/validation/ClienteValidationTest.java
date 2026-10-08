package com.proyecto.servicios.validation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.proyecto.servicios.model.ClienteRequest;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.junit.jupiter.api.Assertions.*;

class ClienteValidationTest {
    private static ValidatorFactory factory;
    private static Validator validator;
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

    @BeforeAll static void preparar() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }
    @AfterAll static void cerrar() { factory.close(); }

    private ObjectNode registro() throws Exception {
        return (ObjectNode) mapper.readTree("""
            {"primerNombre":"Ana","apellidoPaterno":"Pérez","apellidoMaterno":"Ruiz",
             "fechaNacimiento":"1990-01-01","curp":"PERA900101MDFRZN01","rfc":"PERA900101AB1",
             "sexo":"Femenino","nacionalidad":"Argentina","estadoCivil":"Soltero",
             "correoElectronico":"ana@example.com","telefonoMovil":"0123456789",
             "ocupacion":"Ingeniera","empresa":"Empresa","ingresoMensual":12000.50,
             "domicilio":{"calle":"Reforma","numeroExterior":"10","colonia":"Centro",
             "municipio":"Cuauhtemoc","estado":"Ciudad de Mexico","codigoPostal":"06000","pais":"Mexico"},
             "password":"Segura123!"}
            """);
    }

    @Test void aceptaOpcionalesOmitidosNacionalidadLibreYCerosIniciales() throws Exception {
        ClienteRequest request = mapper.treeToValue(registro(), ClienteRequest.class);
        assertTrue(validator.validate(request).isEmpty());
        assertEquals("Argentina", request.nacionalidad());
        assertEquals("0123456789", request.telefonoMovil());
        assertEquals("06000", request.domicilio().codigoPostal());
    }

    @ParameterizedTest
    @CsvSource({"primerNombre,A1", "primerNombre,' A '", "apellidoPaterno,'  '",
        "apellidoMaterno,Ruiz2", "segundoNombre,'  '", "curp,ABC", "rfc,ABC",
        "correoElectronico,invalido", "telefonoMovil,123456789", "telefonoAlternativo,12345678901",
        "nacionalidad,' '", "ingresoMensual,0", "ingresoMensual,-1", "ingresoMensual,1.001",
        "fechaNacimiento,2999-01-01"})
    void rechazaCampoInvalido(String campo, String valor) throws Exception {
        ObjectNode body = registro().put(campo, valor);
        assertTrue(validator.validate(mapper.treeToValue(body, ClienteRequest.class)).stream()
            .anyMatch(v -> v.getPropertyPath().toString().equals(campo)));
    }

    @Test void validaDomicilioAnidadoYLimitesDeLongitud() throws Exception {
        ObjectNode body = registro().put("primerNombre", "A".repeat(51))
            .put("correoElectronico", "a".repeat(95) + "@example.com");
        ((ObjectNode) body.get("domicilio")).put("codigoPostal", "6000");
        var errores = validator.validate(mapper.treeToValue(body, ClienteRequest.class));
        assertTrue(errores.stream().anyMatch(v -> v.getPropertyPath().toString().equals("primerNombre")));
        assertTrue(errores.stream().anyMatch(v -> v.getPropertyPath().toString().equals("correoElectronico")));
        assertTrue(errores.stream().anyMatch(v -> v.getPropertyPath().toString().equals("domicilio.codigoPostal")));
    }
}
