package com.proyecto.servicios.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.proyecto.servicios.models.response.gestopago.GestoPagoPagosResponse;
import com.proyecto.servicios.utils.CadenaAleatoria;
import com.proyecto.servicios.utils.FirmaGestoPago;
import feign.FeignException;
import feign.Response;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Envía la transacción firmada a sendTx.do y la confirma con confirmTx.do. GestoPago responde XML
 * (un token vencido responde 403 con JSON); los campos se buscan por nombre en cualquier nivel.
 */
@Component
@Slf4j
public class GestoPagoPagosClient {

    private final GestoPagoPagosFeignClient feignClient;
    private final ObjectMapper mapper;
    private final FirmaGestoPago firma;
    private final String parametroFirma;

    public GestoPagoPagosClient(GestoPagoPagosFeignClient feignClient, ObjectMapper mapper, FirmaGestoPago firma,
            @Value("${gestopago.pagos.parametro-firma:signed}") String parametroFirma) {
        this.feignClient = feignClient;
        this.mapper = mapper;
        this.firma = firma;
        this.parametroFirma = parametroFirma;
    }

    public GestoPagoPagosResponse enviar(String token, String peticionFirmada) {
        return ejecutar("sendTx", () -> feignClient.enviarTransaccion("Bearer " + token,
                formulario(peticionFirmada)));
    }

    /** confirmTx.do: se invoca con los mismos parámetros de sendTx cuando no hubo respuesta. */
    public GestoPagoPagosResponse confirmar(String token, String peticionFirmada) {
        return ejecutar("confirmTx", () -> feignClient.confirmarTransaccion("Bearer " + token,
                formulario(peticionFirmada)));
    }

    private Map<String, String> formulario(String peticionFirmada) {
        // "random" es una cadena aleatoria; la documentación sugiere el timestamp actual.
        return Map.of("random", System.currentTimeMillis() + "_" + CadenaAleatoria.generar(6),
                parametroFirma, peticionFirmada);
    }

    private GestoPagoPagosResponse ejecutar(String operacion, Supplier<Response> llamada) {
        long inicio = System.nanoTime();
        Integer statusHttp = null;
        log.info("Inicia {} en GestoPago", operacion);
        try {
            Response response = llamada.get();
            if (response == null) {
                throw respuestaInvalida("GestoPago devolvió una respuesta vacía", null, null);
            }
            statusHttp = response.status();
            try (response) {
                if (statusHttp < 200 || statusHttp >= 300) {
                    throw errorHttp(statusHttp, null);
                }
                if (response.body() == null) {
                    throw respuestaInvalida("GestoPago devolvió una respuesta vacía", statusHttp, null);
                }
                try (InputStream cuerpo = response.body().asInputStream()) {
                    return interpretar(new String(cuerpo.readAllBytes(), StandardCharsets.UTF_8), statusHttp);
                }
            }
        } catch (FeignException e) {
            GestoPagoPagoException error = traducirFeign(e);
            statusHttp = error.getStatusHttp();
            registrarError(error, inicio);
            throw error;
        } catch (IOException e) {
            GestoPagoPagoException error = respuestaInvalida("No se pudo leer la respuesta de GestoPago", statusHttp, e);
            registrarError(error, inicio);
            throw error;
        } catch (GestoPagoPagoException e) {
            registrarError(e, inicio);
            throw e;
        } finally {
            log.info("Finaliza {} en GestoPago: statusHttp={}, duraciónMs={}",
                    operacion, statusHttp, duracionMs(inicio));
        }
    }

    GestoPagoPagosResponse interpretar(String cuerpo, Integer statusHttp) {
        String texto = cuerpo == null ? "" : cuerpo.strip();
        if (texto.isEmpty()) {
            throw respuestaInvalida("GestoPago devolvió una respuesta vacía", statusHttp, null);
        }
        Map<String, String> campos;
        try {
            campos = leerCampos(texto);
            if (!campos.containsKey("CODIGO")) {
                // Si la respuesta también viene firmada, se descifra y se vuelve a leer.
                campos = leerCampos(firma.descifrar(texto).strip());
            }
        } catch (RuntimeException e) {
            throw respuestaInvalida("La respuesta de GestoPago no se pudo interpretar", statusHttp, e);
        }
        if (campos.get("CODIGO") == null || campos.get("CODIGO").isBlank()) {
            throw respuestaInvalida("La respuesta de GestoPago no incluye CODIGO", statusHttp, null);
        }
        GestoPagoPagosResponse respuesta = mapear(campos);
        respuesta.setRespuestaOriginal(texto);
        return respuesta;
    }

    private Map<String, String> leerCampos(String texto) {
        Map<String, String> campos = new HashMap<>();
        if (texto.startsWith("<")) {
            leerXml(texto, campos);
        } else if (texto.startsWith("{")) {
            try {
                leerJson(mapper.readTree(texto), campos);
            } catch (IOException e) {
                throw new IllegalArgumentException("JSON inválido", e);
            }
        }
        return campos;
    }

    private void leerXml(String texto, Map<String, String> campos) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setExpandEntityReferences(false);
            Document documento = factory.newDocumentBuilder()
                    .parse(new ByteArrayInputStream(texto.getBytes(StandardCharsets.UTF_8)));
            agregarHojas(documento.getDocumentElement(), campos);
        } catch (Exception e) {
            throw new IllegalArgumentException("XML inválido", e);
        }
    }

    private void agregarHojas(Element elemento, Map<String, String> campos) {
        NodeList hijos = elemento.getChildNodes();
        boolean tieneElementos = false;
        for (int i = 0; i < hijos.getLength(); i++) {
            if (hijos.item(i).getNodeType() == Node.ELEMENT_NODE) {
                tieneElementos = true;
                agregarHojas((Element) hijos.item(i), campos);
            }
        }
        if (!tieneElementos) {
            campos.putIfAbsent(clave(elemento.getTagName()), elemento.getTextContent().strip());
        }
    }

    private void leerJson(JsonNode nodo, Map<String, String> campos) {
        Iterator<Map.Entry<String, JsonNode>> propiedades = nodo.fields();
        while (propiedades.hasNext()) {
            Map.Entry<String, JsonNode> propiedad = propiedades.next();
            JsonNode valor = propiedad.getValue();
            if (valor.isObject()) {
                leerJson(valor, campos);
            } else if (!valor.isNull() && !valor.isArray()) {
                campos.putIfAbsent(clave(propiedad.getKey()), valor.asText().strip());
            }
        }
    }

    private String clave(String nombre) {
        return nombre.toUpperCase(Locale.ROOT);
    }

    private GestoPagoPagosResponse mapear(Map<String, String> campos) {
        GestoPagoPagosResponse respuesta = new GestoPagoPagosResponse();
        respuesta.setCodigo(campos.get("CODIGO").strip());
        respuesta.setIdTx(entero(campos.get("ID_TX")));
        respuesta.setNumAutorizacion(vacioANulo(campos.get("NUM_AUTORIZACION")));
        respuesta.setSaldo(decimal(campos.get("SALDO")));
        respuesta.setSaldoF(vacioANulo(campos.get("SALDO_F")));
        respuesta.setComision(decimal(campos.get("COMISION")));
        respuesta.setComisionF(vacioANulo(campos.get("COMISION_F")));
        respuesta.setFecha(vacioANulo(campos.get("FECHA")));
        respuesta.setMonto(decimal(campos.get("MONTO")));
        respuesta.setReferencia(vacioANulo(campos.get("REFERENCIA")));
        respuesta.setPin(vacioANulo(campos.get("PIN")));
        respuesta.setLegend(vacioANulo(campos.get("LEGEND")));
        String mensaje = vacioANulo(campos.get("TEXTO"));
        respuesta.setMensaje(mensaje != null ? mensaje : vacioANulo(campos.get("MENSAJE")));
        return respuesta;
    }

    private Long entero(String valor) {
        BigDecimal numero = decimal(valor);
        try {
            return numero == null ? null : numero.longValueExact();
        } catch (ArithmeticException e) {
            return null;
        }
    }

    // Acepta notación científica (1.2345E3) y separadores de miles.
    private BigDecimal decimal(String valor) {
        String limpio = vacioANulo(valor);
        if (limpio == null) {
            return null;
        }
        try {
            return new BigDecimal(limpio.replace(",", "").replace("$", ""));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String vacioANulo(String valor) {
        return valor == null || valor.isBlank() ? null : valor.strip();
    }

    private GestoPagoPagoException traducirFeign(FeignException e) {
        if (e.status() >= 0) {
            return errorHttp(e.status(), e);
        }
        // Si ni siquiera se pudo conectar, GestoPago no recibió la petición y es seguro devolver el saldo.
        if (sinConexion(e)) {
            return new GestoPagoPagoException("No se pudo establecer comunicación con GestoPago",
                    GestoPagoPagoException.Tipo.COMUNICACION, null, false, e);
        }
        boolean timeout = contieneTimeout(e);
        return new GestoPagoPagoException(
                timeout ? "Se agotó el tiempo de espera de la respuesta de GestoPago"
                        : "Se interrumpió la comunicación con GestoPago",
                timeout ? GestoPagoPagoException.Tipo.TIMEOUT : GestoPagoPagoException.Tipo.COMUNICACION,
                null, true, e);
    }

    private GestoPagoPagoException errorHttp(int status, Throwable e) {
        if (status == 401 || status == 403) {
            return new GestoPagoPagoException("GestoPago rechazó la autenticación del pago",
                    GestoPagoPagoException.Tipo.AUTENTICACION, status, false, e);
        }
        // 4xx: la petición fue rechazada antes de procesarse. 5xx/408: pudo procesarse.
        boolean incierto = status >= 500 || status == 408;
        return new GestoPagoPagoException("GestoPago devolvió una respuesta HTTP no exitosa",
                GestoPagoPagoException.Tipo.HTTP, status, incierto, e);
    }

    private GestoPagoPagoException respuestaInvalida(String mensaje, Integer status, Throwable e) {
        return new GestoPagoPagoException(mensaje, GestoPagoPagoException.Tipo.RESPUESTA_INVALIDA, status, true, e);
    }

    private boolean sinConexion(Throwable e) {
        for (Throwable actual = e; actual != null; actual = actual.getCause()) {
            if (actual instanceof ConnectException || actual instanceof UnknownHostException) {
                return true;
            }
            if (actual instanceof SocketTimeoutException && actual.getMessage() != null
                    && actual.getMessage().toLowerCase(Locale.ROOT).contains("connect")) {
                return true;
            }
        }
        return false;
    }

    private boolean contieneTimeout(Throwable e) {
        for (Throwable actual = e; actual != null; actual = actual.getCause()) {
            if (actual instanceof SocketTimeoutException
                    || actual.getClass().getSimpleName().toLowerCase(Locale.ROOT).contains("timeout")) {
                return true;
            }
        }
        return false;
    }

    private void registrarError(GestoPagoPagoException e, long inicio) {
        // No se registra el mensaje de Feign: puede incluir cabeceras (token) o el cuerpo firmado.
        log.error("Falló el envío de transacción a GestoPago: tipo={}, statusHttp={}, incierto={}, duraciónMs={}",
                e.getTipo(), e.getStatusHttp(), e.isResultadoIncierto(), duracionMs(inicio));
    }

    private long duracionMs(long inicio) {
        return Duration.ofNanos(System.nanoTime() - inicio).toMillis();
    }
}
