package com.proyecto.servicios.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Serializa la petición a JSON y la firma con AES-256-CBC usando la llave e IV del distribuidor. */
@Component
public class FirmaGestoPago {

    private final ObjectMapper mapper;
    private final String llave;
    private final String iv;

    public FirmaGestoPago(ObjectMapper mapper,
            @Value("${gestopago.pagos.secret_key}") String llave,
            @Value("${gestopago.pagos.iv}") String iv) {
        this.mapper = mapper;
        this.llave = llave;
        this.iv = iv;
    }

    public String serializar(Object peticion) {
        try {
            return mapper.writeValueAsString(peticion);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("No se pudo serializar la petición para GestoPago", e);
        }
    }

    /** Firma un JSON ya serializado; confirmTx debe reenviar exactamente el mismo JSON que sendTx. */
    public String firmarJson(String json) {
        return Encriptar.AESCipher(Encriptar.Mode.ENCRYPT, json, iv, llave);
    }

    public String firmar(Object peticion) {
        return firmarJson(serializar(peticion));
    }

    public String descifrar(String textoFirmado) {
        return Encriptar.AESCipher(Encriptar.Mode.DECRYPT, textoFirmado, iv, llave);
    }
}
