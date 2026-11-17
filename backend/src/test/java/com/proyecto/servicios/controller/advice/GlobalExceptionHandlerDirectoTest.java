package com.proyecto.servicios.controller.advice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.proyecto.servicios.models.response.GenericResponse;
import com.proyecto.servicios.service.exception.AutenticacionException;
import com.proyecto.servicios.service.exception.AutenticacionException.Tipo;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.context.support.DefaultMessageSourceResolvable;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.validation.method.ParameterValidationResult;

/** Invoca el manejador directamente para cubrir las ramas que MockMvc no alcanza con facilidad. */
class GlobalExceptionHandlerDirectoTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @ParameterizedTest
    @CsvSource({
            "USUARIO_DUPLICADO,409,El usuario ya está registrado",
            "CREDENCIALES_INVALIDAS,401,Usuario o contraseña incorrectos",
            "CUENTA_INACTIVA,403,El usuario o cliente está inactivo"})
    void laAutenticacionTraduceCadaTipo(Tipo tipo, int http, String mensaje) {
        ResponseEntity<GenericResponse<Void>> respuesta = handler.autenticacion(new AutenticacionException(tipo));

        assertEquals(http, respuesta.getStatusCode().value());
        assertEquals(mensaje, respuesta.getBody().getMensaje());
    }

    @ParameterizedTest
    @CsvSource({
            "400,'Solicitud inválida: revisa los campos, tipos y valores permitidos'",
            "404,Ruta no encontrada",
            "406,La API devuelve sus respuestas en formato JSON",
            "409,No fue posible procesar la solicitud",
            "415,Tipo de contenido no permitido",
            "500,Ocurrió un error interno en la API",
            "503,Ocurrió un error interno en la API"})
    void losErroresDeSpringConservanSuEstadoConMensajePropio(int estado, String mensaje) {
        ResponseEntity<GenericResponse<Void>> respuesta =
                handler.interno(new ErrorResponseException(HttpStatus.valueOf(estado)));

        assertEquals(estado, respuesta.getStatusCode().value());
        assertEquals(mensaje, respuesta.getBody().getMensaje());
    }

    @Test
    void unaValidacionDeArgumentosListaCadaParametro() {
        MethodParameter conNombre = mock(MethodParameter.class);
        when(conNombre.getParameterName()).thenReturn("telefono");
        MethodParameter sinNombre = mock(MethodParameter.class);
        FieldError campo = new FieldError("obj", "monto", null, false, null, null, "debe ser positivo");
        MessageSourceResolvable sinMensaje = new DefaultMessageSourceResolvable(new String[] {"c"}, null, null);
        MessageSourceResolvable conMensaje = new DefaultMessageSourceResolvable(new String[] {"c"}, null, "inválido");

        ParameterValidationResult primero = mock(ParameterValidationResult.class);
        when(primero.getMethodParameter()).thenReturn(conNombre);
        when(primero.getResolvableErrors()).thenReturn(List.of(campo, conMensaje));
        ParameterValidationResult segundo = mock(ParameterValidationResult.class);
        when(segundo.getMethodParameter()).thenReturn(sinNombre);
        when(segundo.getResolvableErrors()).thenReturn(List.of(sinMensaje));
        HandlerMethodValidationException excepcion = mock(HandlerMethodValidationException.class);
        when(excepcion.isForReturnValue()).thenReturn(false);
        when(excepcion.getAllValidationResults()).thenReturn(List.of(primero, segundo));

        ResponseEntity<GenericResponse<Void>> respuesta = handler.argumentos(excepcion);

        assertEquals(400, respuesta.getStatusCode().value());
        String mensaje = respuesta.getBody().getMensaje();
        assertTrue(mensaje.contains("monto: debe ser positivo"), mensaje);
        assertTrue(mensaje.contains("telefono: inválido"), mensaje);
        assertTrue(mensaje.contains("solicitud: valor inválido"), mensaje);
    }

    @Test
    void unaValidacionDelValorDeRetornoEsUnErrorInterno() {
        HandlerMethodValidationException excepcion = mock(HandlerMethodValidationException.class);
        when(excepcion.isForReturnValue()).thenReturn(true);
        when(excepcion.getStatusCode()).thenReturn(HttpStatus.INTERNAL_SERVER_ERROR);
        when(excepcion.getHeaders()).thenReturn(new org.springframework.http.HttpHeaders());

        assertEquals(500, handler.argumentos(excepcion).getStatusCode().value());
    }

    @Test
    void unErrorDeEnlaceDeTipoSeReportaComoTipoOFormatoInvalido() {
        org.springframework.validation.BindException bind = new org.springframework.validation.BindException(
                new Object(), "solicitud");
        bind.addError(new FieldError("solicitud", "edad", "abc", true, null, null, "x"));
        bind.addError(new org.springframework.validation.ObjectError("solicitud", "global"));

        String mensaje = handler.validacion(bind).getBody().getMensaje();

        assertTrue(mensaje.contains("edad: tipo o formato inválido"), mensaje);
        assertTrue(mensaje.contains("solicitud: global"), mensaje);
    }

    @Test
    void sinErroresDeCamposElMensajeEsElGenerico() {
        org.springframework.validation.BindException bind = new org.springframework.validation.BindException(
                new Object(), "solicitud");

        assertEquals("Verifique la información capturada e intente nuevamente.",
                handler.validacion(bind).getBody().getMensaje());
    }
}
