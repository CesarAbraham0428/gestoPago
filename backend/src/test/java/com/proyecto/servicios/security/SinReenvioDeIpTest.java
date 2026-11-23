package com.proyecto.servicios.security;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.proyecto.servicios.utils.DireccionCliente;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.http.HttpMessageConvertersAutoConfiguration;
import org.springframework.boot.autoconfigure.web.embedded.EmbeddedWebServerFactoryCustomizerAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.DispatcherServletAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.ServletWebServerFactoryAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.WebMvcAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Con la configuración por omisión (sin proxy) X-Forwarded-For se ignora: un cliente directo no puede falsearlo. */
@SpringBootTest(classes = SinReenvioDeIpTest.Sonda.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SinReenvioDeIpTest {

    @Configuration
    @ImportAutoConfiguration({ServletWebServerFactoryAutoConfiguration.class,
            EmbeddedWebServerFactoryCustomizerAutoConfiguration.class,
            DispatcherServletAutoConfiguration.class, WebMvcAutoConfiguration.class,
            HttpMessageConvertersAutoConfiguration.class})
    @Import(IpController.class)
    static class Sonda {
    }

    @RestController
    static class IpController {
        @GetMapping("/ip")
        String ip(HttpServletRequest request) {
            return DireccionCliente.de(request);
        }
    }

    @LocalServerPort
    private int puerto;

    @Test
    void sinProxyConfiguradoLaCabeceraSeIgnora() throws Exception {
        HttpRequest peticion = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + puerto + "/ip"))
                .header("X-Forwarded-For", "203.0.113.9").build();

        String cuerpo = HttpClient.newHttpClient().send(peticion, HttpResponse.BodyHandlers.ofString()).body();

        assertEquals("127.0.0.1", cuerpo);
    }
}
