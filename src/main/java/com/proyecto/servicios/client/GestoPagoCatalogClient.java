package com.proyecto.servicios.client;

import com.proyecto.servicios.model.gestopago.GestoPagoCatalogResponse;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.Unmarshaller;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.StringReader;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.time.Duration;
import java.util.Locale;
import java.util.List;

@Component
@Slf4j
public class GestoPagoCatalogClient {

    private final RestTemplate restTemplate;
    private final URI endpoint;
    private final JAXBContext jaxbContext;

    public GestoPagoCatalogClient(
            RestTemplateBuilder restTemplateBuilder,
            @Value("${gestopago.auth.url}") String baseUrl,
            @Value("${gestopago.catalog.path:/sistema/service/getProductList.do}") String catalogPath,
            @Value("${gestopago.catalog.connect-timeout-ms:5000}") long connectTimeoutMs,
            @Value("${gestopago.catalog.read-timeout-ms:15000}") long readTimeoutMs) {
        this.restTemplate = restTemplateBuilder
                .setConnectTimeout(Duration.ofMillis(connectTimeoutMs))
                .setReadTimeout(Duration.ofMillis(readTimeoutMs))
                .build();

        String normalizedBaseUrl = baseUrl.replaceAll("/+$", "");
        String normalizedPath = catalogPath.replaceAll("^/+", "");
        this.endpoint = URI.create(normalizedBaseUrl + "/" + normalizedPath);

        try {
            this.jaxbContext = JAXBContext.newInstance(GestoPagoCatalogResponse.class);
        } catch (JAXBException exception) {
            throw new IllegalStateException("No se pudo configurar el lector XML de GestoPago", exception);
        }
    }

    public GestoPagoCatalogResponse consultarCatalogo(String token) {
        long startedAt = System.nanoTime();
        Integer statusHttp = null;
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setAccept(List.of(MediaType.APPLICATION_XML, MediaType.TEXT_XML));

        log.info("Inicia consulta del catálogo en GestoPago");
        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    endpoint,
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    String.class
            );

            statusHttp = response.getStatusCode().value();
            String xml = response.getBody();
            if (xml == null || xml.isBlank()) {
                throw new GestoPagoCatalogException(
                        "GestoPago devolvió una respuesta vacía",
                        GestoPagoCatalogException.Tipo.RESPUESTA_VACIA,
                        statusHttp,
                        null);
            }

            return deserializar(xml);
        } catch (RestClientException exception) {
            GestoPagoCatalogException translated = traducirErrorHttp(exception);
            statusHttp = translated.getStatusHttp();
            log.error("Falló la consulta del catálogo en GestoPago: tipo={}, statusHttp={}, duraciónMs={}",
                    translated.getTipo(), translated.getStatusHttp(), duracionMs(startedAt));
            throw translated;
        } catch (GestoPagoCatalogException exception) {
            log.error("Respuesta de GestoPago inválida: tipo={}, statusHttp={}, duraciónMs={}",
                    exception.getTipo(), exception.getStatusHttp(), duracionMs(startedAt));
            throw exception;
        } finally {
            log.info("Finaliza consulta del catálogo en GestoPago: statusHttp={}, duraciónMs={}",
                    statusHttp, duracionMs(startedAt));
        }
    }

    private GestoPagoCatalogException traducirErrorHttp(RestClientException exception) {
        if (exception instanceof HttpStatusCodeException httpException) {
            int status = httpException.getStatusCode().value();
            GestoPagoCatalogException.Tipo tipo = status == 401 || status == 403
                    ? GestoPagoCatalogException.Tipo.AUTENTICACION
                    : GestoPagoCatalogException.Tipo.HTTP;
            return new GestoPagoCatalogException(
                    tipo == GestoPagoCatalogException.Tipo.AUTENTICACION
                            ? "GestoPago rechazó la autenticación del catálogo"
                            : "GestoPago devolvió una respuesta HTTP no exitosa",
                    tipo,
                    status,
                    exception);
        }

        GestoPagoCatalogException.Tipo tipo = contieneTimeout(exception)
                ? GestoPagoCatalogException.Tipo.TIMEOUT
                : GestoPagoCatalogException.Tipo.COMUNICACION;
        String mensaje = tipo == GestoPagoCatalogException.Tipo.TIMEOUT
                ? "Se agotó el tiempo de espera al consultar GestoPago"
                : "No se pudo establecer comunicación con GestoPago";
        return new GestoPagoCatalogException(mensaje, tipo, exception);
    }

    private boolean contieneTimeout(Throwable exception) {
        Throwable actual = exception;
        while (actual != null) {
            if (actual instanceof SocketTimeoutException
                    || actual.getClass().getSimpleName().toLowerCase(Locale.ROOT).contains("timeout")) {
                return true;
            }
            String message = actual.getMessage();
            if (message != null) {
                String normalized = message.toLowerCase(Locale.ROOT);
                if (normalized.contains("timed out") || normalized.contains("timeout")) {
                    return true;
                }
            }
            actual = actual.getCause();
        }
        return false;
    }

    private long duracionMs(long startedAt) {
        return Duration.ofNanos(System.nanoTime() - startedAt).toMillis();
    }

    private GestoPagoCatalogResponse deserializar(String xml) {
        StringReader stringReader = new StringReader(xml);
        XMLStreamReader xmlReader = null;

        try {
            XMLInputFactory inputFactory = XMLInputFactory.newFactory();
            inputFactory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
            inputFactory.setProperty("javax.xml.stream.isSupportingExternalEntities", false);
            xmlReader = inputFactory.createXMLStreamReader(stringReader);

            Unmarshaller unmarshaller = jaxbContext.createUnmarshaller();
            GestoPagoCatalogResponse response = (GestoPagoCatalogResponse) unmarshaller.unmarshal(xmlReader);
            while (xmlReader.hasNext()) {
                int event = xmlReader.next();
                if (event == XMLStreamConstants.START_ELEMENT
                        || (event == XMLStreamConstants.CHARACTERS && !xmlReader.isWhiteSpace())) {
                    throw new XMLStreamException("Contenido adicional después del elemento raíz");
                }
            }
            return response;
        } catch (JAXBException | XMLStreamException | ClassCastException exception) {
            throw new GestoPagoCatalogException(
                    "La respuesta XML de GestoPago no es válida",
                    GestoPagoCatalogException.Tipo.XML,
                    exception);
        } finally {
            if (xmlReader != null) {
                try {
                    xmlReader.close();
                } catch (XMLStreamException exception) {
                    log.debug("No se pudo cerrar el lector XML de GestoPago", exception);
                }
            }
            stringReader.close();
        }
    }
}
