package com.proyecto.servicios.client;

import com.proyecto.servicios.models.response.gestopago.GestoPagoCatalogResponse;
import feign.FeignException;
import feign.Response;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.PushbackInputStream;
import java.time.Duration;

@Component
@Slf4j
public class GestoPagoCatalogClient {

    private final GestoPagoCatalogFeignClient feignClient;
    private final JAXBContext jaxbContext;

    @Autowired
    public GestoPagoCatalogClient(GestoPagoCatalogFeignClient feignClient) {
        this.feignClient = feignClient;
        this.jaxbContext = crearContexto(GestoPagoCatalogResponse.class);
    }

    static JAXBContext crearContexto(Class<?> clase) {
        try {
            return JAXBContext.newInstance(clase);
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
            statusHttp = response.status();
            return leer(response, statusHttp);
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

    private GestoPagoCatalogResponse leer(Response response, int statusHttp) throws IOException {
        try (response) {
            if (statusHttp != 200) {
                throw errorHttp(statusHttp, null);
            }
            PushbackInputStream xml = response.body() == null ? null
                    : new PushbackInputStream(response.body().asInputStream());
            int primerByte = xml == null ? -1 : xml.read();
            if (primerByte == -1) {
                throw new GestoPagoCatalogException("GestoPago devolvió una respuesta vacía",
                        GestoPagoCatalogException.Tipo.RESPUESTA_VACIA, statusHttp, null);
            }
            xml.unread(primerByte);
            return deserializar(xml);
        }
    }

    private GestoPagoCatalogResponse deserializar(InputStream xml) {
        try {
            XMLInputFactory factory = XMLInputFactory.newFactory();
            factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
            factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
            XMLStreamReader lector = factory.createXMLStreamReader(xml);
            return (GestoPagoCatalogResponse) jaxbContext.createUnmarshaller().unmarshal(lector);
        } catch (JAXBException | XMLStreamException | ClassCastException e) {
            throw errorXml(e);
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
        boolean timeout = CausasRed.contieneTimeout(e);
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

    private void registrarError(GestoPagoCatalogException e, long inicio) {
        // El mensaje y la causa de Feign pueden incluir cabeceras o el cuerpo remoto.
        log.error("Falló la consulta del catálogo: tipo={}, statusHttp={}, duraciónMs={}",
                e.getTipo(), e.getStatusHttp(), duracionMs(inicio));
    }

    private long duracionMs(long inicio) {
        return Duration.ofNanos(System.nanoTime() - inicio).toMillis();
    }
}
