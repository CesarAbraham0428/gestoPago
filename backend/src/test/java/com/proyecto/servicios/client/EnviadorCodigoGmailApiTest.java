package com.proyecto.servicios.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class EnviadorCodigoGmailApiTest {

    private static final String TOKEN_OK = "{\"access_token\":\"ya29.token\",\"expires_in\":3599}";

    private HttpClient http;
    private EnviadorCodigoGmailApi enviador;

    @BeforeEach
    void preparar() {
        http = mock(HttpClient.class);
        enviador = new EnviadorCodigoGmailApi(http, "id", "secreto", "refresco", "remitente@gmail.com");
    }

    @SuppressWarnings("unchecked")
    private static HttpResponse<String> respuesta(int estado, String cuerpo) {
        HttpResponse<String> r = mock(HttpResponse.class);
        when(r.statusCode()).thenReturn(estado);
        when(r.body()).thenReturn(cuerpo);
        return r;
    }

    @SuppressWarnings("unchecked")
    private void responder(HttpResponse<String> primera, HttpResponse<String>... siguientes)
            throws IOException, InterruptedException {
        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(primera, siguientes);
    }

    @SuppressWarnings("unchecked")
    private List<HttpRequest> peticiones(int esperadas) throws IOException, InterruptedException {
        ArgumentCaptor<HttpRequest> captura = ArgumentCaptor.forClass(HttpRequest.class);
        verify(http, times(esperadas)).send(captura.capture(), any(HttpResponse.BodyHandler.class));
        return captura.getAllValues();
    }

    @SuppressWarnings("unchecked")
    @Test
    void obtieneElTokenYEnviaElCorreoConElBearer() throws Exception {
        responder(respuesta(200, TOKEN_OK), respuesta(200, "{\"id\":\"1\"}"));

        enviador.enviar("ana@correo.mx", "123456", Duration.ofMinutes(5));

        List<HttpRequest> enviadas = peticiones(2);
        assertEquals(EnviadorCodigoGmailApi.TOKEN, enviadas.get(0).uri());
        assertEquals(EnviadorCodigoGmailApi.ENVIO, enviadas.get(1).uri());
        assertEquals("Bearer ya29.token", enviadas.get(1).headers().firstValue("Authorization").orElseThrow());
    }

    @SuppressWarnings("unchecked")
    @Test
    void reutilizaElTokenMientrasNoVenza() throws Exception {
        responder(respuesta(200, TOKEN_OK), respuesta(200, "{}"), respuesta(200, "{}"));

        enviador.enviar("ana@correo.mx", "111111", Duration.ofMinutes(5));
        enviador.enviar("ana@correo.mx", "222222", Duration.ofMinutes(5));

        // 1 token + 2 envíos
        List<HttpRequest> enviadas = peticiones(3);
        assertEquals(EnviadorCodigoGmailApi.ENVIO, enviadas.get(2).uri());
    }

    @Test
    void elMensajeMimeLlevaDestinatarioRemitenteYCodigo() {
        String raw = enviador.construirMensaje("ana@correo.mx", "123456", Duration.ofMinutes(5));

        assertFalse(raw.contains("=") || raw.contains("+") || raw.contains("/"), "debe ser Base64 URL-safe");
        String mime = new String(Base64.getUrlDecoder().decode(raw), StandardCharsets.UTF_8);
        assertTrue(mime.contains("To: ana@correo.mx"));
        assertTrue(mime.contains("From: remitente@gmail.com"));
        assertTrue(mime.contains("123456"));
    }

    @Test
    void unDestinatarioInvalidoNoSeEnvia() {
        assertThrows(IllegalStateException.class,
                () -> enviador.construirMensaje("no es un correo", "123456", Duration.ofMinutes(5)));
    }

    @Test
    void siGoogleRechazaLasCredencialesNoEnviaNadaYNoFiltraSecretos() throws Exception {
        responder(respuesta(400, "{\"error\":\"invalid_grant\"}"));

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> enviador.enviar("ana@correo.mx", "123456", Duration.ofMinutes(5)));

        assertTrue(error.getMessage().contains("400"));
        assertFalse(error.getMessage().contains("secreto") || error.getMessage().contains("refresco"));
        peticiones(1);
    }

    @SuppressWarnings("unchecked")
    @Test
    void siGmailRechazaElEnvioLanzaExcepcionParaQueElDesafioNoQuedeVigente() throws Exception {
        responder(respuesta(200, TOKEN_OK), respuesta(403, "{\"error\":\"forbidden\"}"));

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> enviador.enviar("ana@correo.mx", "123456", Duration.ofMinutes(5)));

        assertTrue(error.getMessage().contains("403"));
    }

    @SuppressWarnings("unchecked")
    @Test
    void conTokenRechazadoPideUnoNuevoEnElSiguienteEnvio() throws Exception {
        responder(respuesta(200, TOKEN_OK), respuesta(401, "{}"), respuesta(200, TOKEN_OK), respuesta(200, "{}"));

        assertThrows(IllegalStateException.class,
                () -> enviador.enviar("ana@correo.mx", "123456", Duration.ofMinutes(5)));
        enviador.enviar("ana@correo.mx", "123456", Duration.ofMinutes(5));

        List<HttpRequest> enviadas = peticiones(4);
        assertEquals(EnviadorCodigoGmailApi.TOKEN, enviadas.get(2).uri());
    }

    @Test
    void unFalloDeRedSeConvierteEnExcepcion() throws Exception {
        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenThrow(new IOException("sin red"));

        assertThrows(IllegalStateException.class,
                () -> enviador.enviar("ana@correo.mx", "123456", Duration.ofMinutes(5)));
    }
}
