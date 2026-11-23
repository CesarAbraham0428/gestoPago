package com.proyecto.servicios.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.proyecto.servicios.models.response.gestopago.GestoPagoPagosResponse;
import com.proyecto.servicios.utils.FirmaGestoPago;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.springframework.stereotype.Component;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * Convierte la respuesta de GestoPago (XML, JSON o cifrada) en {@link GestoPagoPagosResponse}. Los campos se
 * buscan por nombre en cualquier nivel.
 */
@Component
class RespuestaPagoParser {

    private final ObjectMapper mapper;
    private final FirmaGestoPago firma;

    RespuestaPagoParser(ObjectMapper mapper, FirmaGestoPago firma) {
        this.mapper = mapper;
        this.firma = firma;
    }

    GestoPagoPagosResponse interpretar(String cuerpo, Integer statusHttp) {
        String texto = cuerpo == null ? "" : cuerpo.strip();
        if (texto.isEmpty()) {
            throw ErroresGestoPagoPagos.respuestaInvalida("GestoPago devolvió una respuesta vacía", statusHttp, null);
        }
        Map<String, String> campos;
        try {
            campos = leerCampos(texto);
            if (!campos.containsKey("CODIGO")) {
                // Si la respuesta también viene firmada, se descifra y se vuelve a leer.
                campos = leerCampos(firma.descifrar(texto).strip());
            }
        } catch (RuntimeException e) {
            throw ErroresGestoPagoPagos.respuestaInvalida("La respuesta de GestoPago no se pudo interpretar",
                    statusHttp, e);
        }
        if (vacioANulo(campos.get("CODIGO")) == null) {
            throw ErroresGestoPagoPagos.respuestaInvalida("La respuesta de GestoPago no incluye CODIGO",
                    statusHttp, null);
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
            agregarHojas(factory.newDocumentBuilder()
                    .parse(new ByteArrayInputStream(texto.getBytes(StandardCharsets.UTF_8)))
                    .getDocumentElement(), campos);
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
}
