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
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.StringReader;
import java.net.URI;
import java.time.Duration;
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
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setAccept(List.of(MediaType.APPLICATION_XML, MediaType.TEXT_XML));

        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    endpoint,
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    String.class
            );

            String xml = response.getBody();
            if (xml == null || xml.isBlank()) {
                throw new GestoPagoCatalogException("GestoPago devolvió una respuesta vacía");
            }

            return deserializar(xml);
        } catch (RestClientException exception) {
            throw new GestoPagoCatalogException("No se pudo consultar el catálogo de GestoPago", exception);
        }
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
            return (GestoPagoCatalogResponse) unmarshaller.unmarshal(xmlReader);
        } catch (JAXBException | XMLStreamException exception) {
            throw new GestoPagoCatalogException("La respuesta XML de GestoPago no es válida", exception);
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