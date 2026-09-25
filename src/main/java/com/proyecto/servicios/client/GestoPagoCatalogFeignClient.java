package com.proyecto.servicios.client;

import feign.Response;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;

@FeignClient(name = "gestopago-catalog", url = "${gestopago.auth.url}")
public interface GestoPagoCatalogFeignClient {

    @GetMapping("${gestopago.catalog.path:/sistema/service/getProductList.do}")
    Response consultarCatalogo(@RequestHeader("Authorization") String authorization);
}
