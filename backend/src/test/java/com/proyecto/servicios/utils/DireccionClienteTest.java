package com.proyecto.servicios.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class DireccionClienteTest {

    @Test
    void unaIpv4SeDejaTalCual() {
        assertEquals("203.0.113.9", DireccionCliente.normalizar("203.0.113.9"));
    }

    @Test
    void lasIpv6DeUnMismoPrefijo64SeAgrupan() {
        String a = DireccionCliente.normalizar("2001:db8:abcd:12:1111:2222:3333:4444");
        String b = DireccionCliente.normalizar("2001:db8:abcd:12:ffff:eeee:dddd:cccc");

        assertEquals("2001:0db8:abcd:0012::/64", a);
        assertEquals(a, b);
        assertEquals(false, a.equals(DireccionCliente.normalizar("2001:db8:abcd:13::1")));
    }

    @Test
    void unaIpv6MapeadaAIpv4SeConvierteEnIpv4() {
        assertEquals("203.0.113.9", DireccionCliente.normalizar("::ffff:203.0.113.9"));
    }

    @Test
    void seIgnoraLaZonaDeUnaIpv6() {
        assertEquals(DireccionCliente.normalizar("fe80::1"), DireccionCliente.normalizar("fe80::1%eth0"));
    }

    @Test
    void unaIpv6InvalidaSeDevuelveSinResolverla() {
        assertEquals("::g", DireccionCliente.normalizar("::g"));
    }

    @Test
    void sinDireccionSeUsaUnValorConstante() {
        assertEquals("desconocida", DireccionCliente.normalizar(null));
        assertEquals("desconocida", DireccionCliente.normalizar("  "));
    }

    @Test
    void usaLaDireccionRemotaDeLaPeticion() {
        MockHttpServletRequest peticion = new MockHttpServletRequest();
        peticion.setRemoteAddr("198.51.100.7");

        assertEquals("198.51.100.7", DireccionCliente.de(peticion));
    }
}
