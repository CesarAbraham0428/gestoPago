package com.proyecto.servicios.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.proyecto.servicios.utils.DireccionCliente;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
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

/**
 * Servidor Tomcat real con la configuración de application.properties: comprueba que, con
 * {@code FORWARD_HEADERS=native}, los límites por IP ven al cliente de {@code X-Forwarded-For} cuando la conexión
 * llega de un proxy de confianza (aquí 127.0.0.1) y que la cabecera no se puede falsificar con un proxy no
 * confiable. Sin esto, detrás de Railway todos los usuarios compartirían la IP del proxy.
 */
@SpringBootTest(classes = IpRealDetrasDeProxyTest.Sonda.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "FORWARD_HEADERS=native")
class IpRealDetrasDeProxyTest {

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

    @Value("${server.tomcat.remoteip.internal-proxies}")
    private String proxiesConfiables;

    private String ipVista(String forwardedFor) throws IOException, InterruptedException {
        HttpRequest.Builder peticion = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + puerto + "/ip"));
        if (forwardedFor != null) {
            peticion.header("X-Forwarded-For", forwardedFor);
        }
        return HttpClient.newHttpClient().send(peticion.build(), HttpResponse.BodyHandlers.ofString()).body();
    }

    @Test
    void conProxyDeConfianzaSeUsaLaIpDelCliente() throws Exception {
        assertEquals("203.0.113.9", ipVista("203.0.113.9"));
    }

    @Test
    void unaIpFalsificadaALaIzquierdaNoEngañaAlLimite() throws Exception {
        // El cliente antepone una IP inventada; el proxy añade la real a la derecha.
        assertEquals("203.0.113.9", ipVista("1.1.1.1, 203.0.113.9"));
    }

    @Test
    void sinCabeceraSeUsaLaConexionDirecta() throws Exception {
        assertEquals("127.0.0.1", ipVista(null));
    }

    @Test
    void elRangoDeConfianzaCubreAlProxyDeRailwayYSoloAProxiesInternos() {
        for (String interno : new String[] {"100.64.0.2", "100.127.255.255", "10.1.2.3", "127.0.0.1", "192.168.1.5",
                "172.16.0.1", "172.31.255.255", "169.254.1.1", "fd12:3456::1", "::1"}) {
            assertTrue(interno.matches(proxiesConfiables), interno + " debe ser proxy interno");
        }
        for (String externo : new String[] {"8.8.8.8", "203.0.113.9", "100.63.255.255", "100.128.0.1", "172.32.0.1",
                "172.15.0.1", "11.0.0.1", "2001:db8::1"}) {
            assertFalse(externo.matches(proxiesConfiables), externo + " no debe ser proxy interno");
        }
    }
}
