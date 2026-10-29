package com.proyecto.servicios.client;

import feign.Response;
import java.util.Map;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

/** El formulario (x-www-form-urlencoded) lleva "random" y "signed" (el JSON cifrado). */
@FeignClient(name = "gestopago-pagos", url = "${gestopago.pagos.url}",
        configuration = GestoPagoPagosFeignConfig.class)
public interface GestoPagoPagosFeignClient {

    @PostMapping(value = "${gestopago.pagos.path:/sistema/service/sendTx.do}",
            consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    Response enviarTransaccion(@RequestHeader("Authorization") String authorization,
            @RequestBody Map<String, ?> formulario);

    @PostMapping(value = "${gestopago.pagos.confirm-path:/sistema/service/confirmTx.do}",
            consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    Response confirmarTransaccion(@RequestHeader("Authorization") String authorization,
            @RequestBody Map<String, ?> formulario);

    @PostMapping(value = "${gestopago.pagos.verify-path:/sistema/service/verifyReference.do}",
            consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    Response verificarReferencia(@RequestHeader("Authorization") String authorization,
            @RequestBody Map<String, ?> formulario);
}
