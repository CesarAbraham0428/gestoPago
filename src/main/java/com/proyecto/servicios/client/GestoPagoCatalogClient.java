package com.proyecto.servicios.client;

import com.proyecto.servicios.model.gestopago.GestoPagoCatalogResponse;
import feign.FeignException;
import feign.Response;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.Unmarshaller;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.PushbackInputStream;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.Locale;

@Component
@Slf4j
public class GestoPagoCatalogClient {

    private GestoPagoCatalogFeignClient feignClient;
    private JAXBContext jaxbContext;

    @Autowired
    public GestoPagoCatalogClient(GestoPagoCatalogFeignClient feignClient) {
        this.feignClient = feignClient;
        try {
            jaxbContext = JAXBContext.newInstance(GestoPagoCatalogResponse.class);
        } catch (JAXBException e) {
            throw new IllegalStateException("No se pudo configurar el lector XML de GestoPago", e);
        }
    }

    public GestoPagoCatalogResponse consultarCatalogo(String token) {
        long inicio = System.nanoTime();
        Integer statusHttp = null;
        log.info("Inicia consulta del catálogo en GestoPago");
        try {
            Response response = feignClient.consultarCatalogo("Bearer " + token);
            if (response == null) {
                throw new GestoPagoCatalogException("GestoPago devolvió una respuesta vacía",
                        GestoPagoCatalogException.Tipo.RESPUESTA_VACIA, null);
            }
            statusHttp = response.status();
            try (response) {
                if (statusHttp != 200) {
                    throw errorHttp(statusHttp, null);
                }
                if (response.body() == null) {
                    throw new GestoPagoCatalogException("GestoPago devolvió una respuesta vacía",
                            GestoPagoCatalogException.Tipo.RESPUESTA_VACIA, statusHttp, null);
                }
                PushbackInputStream xml = new PushbackInputStream(response.body().asInputStream());
                int primerByte = xml.read();
                if (primerByte == -1) {
                    throw new GestoPagoCatalogException("GestoPago devolvió una respuesta vacía",
                            GestoPagoCatalogException.Tipo.RESPUESTA_VACIA, statusHttp, null);
                }
                xml.unread(primerByte);
                return deserializar(xml);
            }
        } catch (FeignException e) {
            GestoPagoCatalogException error = traducirFeign(e);
            statusHttp = error.getStatusHttp();
            registrarError(error, inicio);
            throw error;
        } catch (IOException e) {
            GestoPagoCatalogException error = new GestoPagoCatalogException(
                    "No se pudo leer la respuesta de GestoPago",
                    GestoPagoCatalogException.Tipo.COMUNICACION, statusHttp, e);
            registrarError(error, inicio);
            throw error;
        } catch (GestoPagoCatalogException e) {
            registrarError(e, inicio);
            throw e;
        } finally {
            log.info("Finaliza consulta del catálogo en GestoPago: statusHttp={}, duraciónMs={}",
                    statusHttp, duracionMs(inicio));
        }
    }

    private GestoPagoCatalogResponse deserializar(InputStream xml) {
        XMLStreamReader lector = null;
        try {
            XMLInputFactory factory = XMLInputFactory.newFactory();
            factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
            factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
            lector = factory.createXMLStreamReader(xml);
            Unmarshaller unmarshaller = jaxbContext.createUnmarshaller();
            GestoPagoCatalogResponse catalogo = (GestoPagoCatalogResponse) unmarshaller.unmarshal(lector);
            while (lector.hasNext()) {
                int evento = lector.next();
                if (evento == XMLStreamConstants.START_ELEMENT
                        || (evento == XMLStreamConstants.CHARACTERS && !lector.isWhiteSpace())) {
                    throw new XMLStreamException("Contenido adicional después del elemento raíz");
                }
            }
            return catalogo;
        } catch (JAXBException | XMLStreamException | ClassCastException e) {
            throw errorXml(e);
        } finally {
            if (lector != null) {
                try {
                    lector.close();
                } catch (XMLStreamException e) {
                    log.debug("No se pudo cerrar el lector XML de GestoPago: origen={}", origen(e));
                }
            }
        }
    }

    private GestoPagoCatalogException errorXml(Exception e) {
        return new GestoPagoCatalogException("La respuesta XML de GestoPago no es válida",
                GestoPagoCatalogException.Tipo.XML, 200, e);
    }

    private GestoPagoCatalogException traducirFeign(FeignException e) {
        if (e.status() >= 0) {
            return errorHttp(e.status(), e);
        }
        boolean timeout = contieneTimeout(e);
        return new GestoPagoCatalogException(
                timeout ? "Se agotó el tiempo de espera al consultar GestoPago"
                        : "No se pudo establecer comunicación con GestoPago",
                timeout ? GestoPagoCatalogException.Tipo.TIMEOUT
                        : GestoPagoCatalogException.Tipo.COMUNICACION,
                null, e);
    }

    private GestoPagoCatalogException errorHttp(int status, Throwable e) {
        boolean autenticacion = status == 401 || status == 403;
        return new GestoPagoCatalogException(
                autenticacion ? "GestoPago rechazó la autenticación del catálogo"
                        : "GestoPago devolvió una respuesta HTTP no exitosa",
                autenticacion ? GestoPagoCatalogException.Tipo.AUTENTICACION
                        : GestoPagoCatalogException.Tipo.HTTP,
                status, e);
    }

    private boolean contieneTimeout(Throwable e) {
        Throwable actual = e;
        while (actual != null) {
            if (actual instanceof SocketTimeoutException
                    || actual.getClass().getSimpleName().toLowerCase(Locale.ROOT).contains("timeout")) {
                return true;
            }
            actual = actual.getCause();
        }
        return false;
    }

    private void registrarError(GestoPagoCatalogException e, long inicio) {
        // El mensaje y la causa de Feign pueden incluir cabeceras o el cuerpo remoto.
        log.error("Falló la consulta del catálogo: tipo={}, statusHttp={}, origen={}, duraciónMs={}",
                e.getTipo(), e.getStatusHttp(), origen(e), duracionMs(inicio));
    }

    private String origen(Throwable e) {
        Throwable causa = e;
        while (causa.getCause() != null) {
            causa = causa.getCause();
        }
        StackTraceElement[] traza = causa.getStackTrace();
        return traza.length == 0 ? "desconocido" : traza[0].getClassName() + ":" + traza[0].getLineNumber();
    }

    private long duracionMs(long inicio) {
        return Duration.ofNanos(System.nanoTime() - inicio).toMillis();
    }
}
