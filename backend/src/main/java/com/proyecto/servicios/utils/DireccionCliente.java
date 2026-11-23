package com.proyecto.servicios.utils;

import jakarta.servlet.http.HttpServletRequest;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * IP del cliente para los límites de solicitudes. Con {@code FORWARD_HEADERS=native} Tomcat ya reemplaza
 * {@code getRemoteAddr()} por la IP real que reportó un proxy de confianza; aquí solo se normaliza. Las IPv6 se
 * agrupan por prefijo /64: una sola conexión suele recibir un /64 completo y rotar dentro de él evadiría el límite.
 */
public final class DireccionCliente {

    private static final String DESCONOCIDA = "desconocida";

    private DireccionCliente() {
    }

    public static String de(HttpServletRequest request) {
        return normalizar(request.getRemoteAddr());
    }

    static String normalizar(String direccion) {
        if (direccion == null || direccion.isBlank()) {
            return DESCONOCIDA;
        }
        String limpia = direccion.strip();
        int zona = limpia.indexOf('%');
        if (zona >= 0) {
            limpia = limpia.substring(0, zona);
        }
        if (!limpia.contains(":")) {
            return limpia;
        }
        try {
            // Es una IP literal: no hay resolución DNS.
            InetAddress ip = InetAddress.getByName(limpia);
            if (!(ip instanceof Inet6Address)) {
                return ip.getHostAddress(); // IPv6 mapeada a IPv4 (::ffff:1.2.3.4)
            }
            byte[] bytes = ip.getAddress();
            return String.format("%02x%02x:%02x%02x:%02x%02x:%02x%02x::/64", bytes[0], bytes[1], bytes[2], bytes[3],
                    bytes[4], bytes[5], bytes[6], bytes[7]);
        } catch (UnknownHostException e) {
            return limpia;
        }
    }
}
