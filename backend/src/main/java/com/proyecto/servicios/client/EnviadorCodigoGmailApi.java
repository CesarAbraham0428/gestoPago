package com.proyecto.servicios.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.proyecto.servicios.service.EnviadorCodigo;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Properties;

/**
 * Entrega el código con la API HTTPS de Gmail (puerto 443), para hosts que bloquean el SMTP (p. ej. Render gratis).
 * Usa OAuth 2.0 con un refresh token de la cuenta remitente y el alcance {@code gmail.send}; el mensaje se
 * envía desde esa cuenta. Ningún secreto se incluye en los mensajes de error.
 */
public class EnviadorCodigoGmailApi implements EnviadorCodigo {

    static final URI TOKEN = URI.create("https://oauth2.googleapis.com/token");
    static final URI ENVIO = URI.create("https://gmail.googleapis.com/gmail/v1/users/me/messages/send");
    private static final Duration TIEMPO_ESPERA = Duration.ofSeconds(10);
    /** Margen para renovar el token de acceso antes de que venza. */
    private static final Duration MARGEN = Duration.ofSeconds(60);

    private final HttpClient http;
    private final ObjectMapper json = new ObjectMapper();
    private final String clientId;
    private final String clientSecret;
    private final String refreshToken;
    private final String remitente;

    private String tokenDeAcceso;
    private Instant venceToken = Instant.EPOCH;

    public EnviadorCodigoGmailApi(HttpClient http, String clientId, String clientSecret, String refreshToken,
            String remitente) {
        this.http = http;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.refreshToken = refreshToken;
        this.remitente = remitente;
    }

    public static EnviadorCodigoGmailApi conClienteHttpPorOmision(String clientId, String clientSecret,
            String refreshToken, String remitente) {
        HttpClient cliente = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        return new EnviadorCodigoGmailApi(cliente, clientId, clientSecret, refreshToken, remitente);
    }

    @Override
    public void enviar(String destinatario, String codigo, Duration vigencia) {
        String cuerpo;
        try {
            cuerpo = json.writeValueAsString(Map.of("raw", construirMensaje(destinatario, codigo, vigencia)));
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo preparar el correo del código", e);
        }
        HttpRequest peticion = HttpRequest.newBuilder(ENVIO).timeout(TIEMPO_ESPERA)
                .header("Authorization", "Bearer " + tokenDeAcceso())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(cuerpo)).build();
        HttpResponse<String> respuesta = ejecutar(peticion);
        if (respuesta.statusCode() / 100 != 2) {
            if (respuesta.statusCode() == 401) {
                invalidarToken();
            }
            throw new IllegalStateException("La API de Gmail rechazó el envío (HTTP " + respuesta.statusCode() + ")");
        }
    }

    /** Mensaje MIME en Base64 URL-safe, el formato que espera {@code messages.send}. */
    String construirMensaje(String destinatario, String codigo, Duration vigencia) {
        try {
            MimeMessage mensaje = new MimeMessage(Session.getInstance(new Properties()));
            mensaje.setFrom(new InternetAddress(remitente, true));
            mensaje.setRecipient(Message.RecipientType.TO, new InternetAddress(destinatario, true));
            mensaje.setSubject(TextoCodigo.ASUNTO, StandardCharsets.UTF_8.name());
            mensaje.setText(TextoCodigo.cuerpo(codigo, vigencia), StandardCharsets.UTF_8.name());
            ByteArrayOutputStream salida = new ByteArrayOutputStream();
            mensaje.writeTo(salida);
            return Base64.getUrlEncoder().withoutPadding().encodeToString(salida.toByteArray());
        } catch (MessagingException | IOException e) {
            throw new IllegalStateException("No se pudo construir el correo del código", e);
        }
    }

    private synchronized String tokenDeAcceso() {
        if (tokenDeAcceso != null && Instant.now().isBefore(venceToken.minus(MARGEN))) {
            return tokenDeAcceso;
        }
        String formulario = "grant_type=refresh_token"
                + "&client_id=" + codificar(clientId)
                + "&client_secret=" + codificar(clientSecret)
                + "&refresh_token=" + codificar(refreshToken);
        HttpRequest peticion = HttpRequest.newBuilder(TOKEN).timeout(TIEMPO_ESPERA)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(formulario)).build();
        HttpResponse<String> respuesta = ejecutar(peticion);
        if (respuesta.statusCode() / 100 != 2) {
            throw new IllegalStateException("Google no entregó el token de acceso (HTTP " + respuesta.statusCode()
                    + "); revisa GMAIL_CLIENT_ID, GMAIL_CLIENT_SECRET y GMAIL_REFRESH_TOKEN");
        }
        try {
            JsonNode datos = json.readTree(respuesta.body());
            String token = datos.path("access_token").asText("");
            if (token.isBlank()) {
                throw new IllegalStateException("La respuesta de Google no incluye access_token");
            }
            tokenDeAcceso = token;
            venceToken = Instant.now().plusSeconds(datos.path("expires_in").asLong(3600));
            return tokenDeAcceso;
        } catch (IOException e) {
            throw new IllegalStateException("Respuesta de token ilegible", e);
        }
    }

    private synchronized void invalidarToken() {
        tokenDeAcceso = null;
        venceToken = Instant.EPOCH;
    }

    private HttpResponse<String> ejecutar(HttpRequest peticion) {
        try {
            return http.send(peticion, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo contactar con Google", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Envío interrumpido", e);
        }
    }

    private static String codificar(String valor) {
        return URLEncoder.encode(valor, StandardCharsets.UTF_8);
    }
}
