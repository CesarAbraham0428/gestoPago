package com.proyecto.servicios.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.proyecto.servicios.models.response.NotificacionResponse;
import com.proyecto.servicios.service.NotificacionService.NotificacionCreada;
import com.proyecto.servicios.service.NotificadorSesiones.SesionTerminada;
import java.time.Instant;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

class NotificadorSesionesTest {

    private final NotificadorSesiones notificador = new NotificadorSesiones(1_000);

    /** Emisor que registra lo enviado y si se cerró, o que falla al enviar. */
    private static final class EmisorDePrueba extends SseEmitter {
        final List<String> enviados = new ArrayList<>();
        boolean completado;
        final RuntimeException errorEnvio;
        final boolean fallaConIo;

        EmisorDePrueba() {
            this(null, false);
        }

        EmisorDePrueba(RuntimeException errorEnvio, boolean fallaConIo) {
            this.errorEnvio = errorEnvio;
            this.fallaConIo = fallaConIo;
        }

        @Override
        public void send(SseEventBuilder evento) throws IOException {
            if (fallaConIo) {
                throw new IOException("conexión perdida");
            }
            if (errorEnvio != null) {
                throw errorEnvio;
            }
            Set<DataWithMediaType> partes = evento.build();
            StringBuilder texto = new StringBuilder();
            for (DataWithMediaType parte : partes) {
                texto.append(parte.getData());
            }
            enviados.add(texto.toString());
        }

        @Override
        public void send(Object objeto, MediaType tipo) {
            enviados.add(String.valueOf(objeto));
        }

        @Override
        public synchronized void complete() {
            completado = true;
        }

        @Override
        public synchronized void completeWithError(Throwable ex) {
            completado = true;
        }
    }

    @Test
    void alConectarEnviaUnEventoInicial() {
        EmisorDePrueba emisor = new EmisorDePrueba();

        assertSame(emisor, notificador.suscribir(1, "jti-a", emisor));

        assertEquals(1, emisor.enviados.size());
        assertTrue(emisor.enviados.get(0).contains("event:conectado"));
        assertEquals(1, notificador.abiertas());
    }

    @Test
    void reemplazarLaSesionSoloAvisaAlDispositivoAnterior() {
        EmisorDePrueba anterior = new EmisorDePrueba();
        EmisorDePrueba nuevo = new EmisorDePrueba();
        EmisorDePrueba otroUsuario = new EmisorDePrueba();
        notificador.suscribir(1, "viejo", anterior);
        notificador.suscribir(1, "nuevo", nuevo);
        notificador.suscribir(2, "ajeno", otroUsuario);

        notificador.alTerminarSesion(new SesionTerminada(1, "nuevo", "otro dispositivo"));

        assertTrue(anterior.completado);
        String aviso = anterior.enviados.get(1);
        assertTrue(aviso.contains("event:" + NotificadorSesiones.EVENTO_CIERRE));
        assertTrue(aviso.contains("data:otro dispositivo"));
        assertEquals(1, nuevo.enviados.size());
        assertEquals(1, otroUsuario.enviados.size());
        assertEquals(2, notificador.abiertas());
    }

    @Test
    void unaNotificacionSoloLlegaAlCanalDelUsuarioYNoLoCierra() {
        EmisorDePrueba suyo = new EmisorDePrueba();
        EmisorDePrueba ajeno = new EmisorDePrueba();
        EmisorDePrueba cortado = new EmisorDePrueba(null, true);
        notificador.suscribir(1, "a", suyo);
        notificador.suscribir(2, "b", ajeno);
        notificador.suscribir(1, "c", cortado);

        notificador.alCrearNotificacion(new NotificacionCreada(1,
            new NotificacionResponse(9, "PAGO_APROBADO", "Pago aprobado", "Detalle", "UPC1", false, Instant.EPOCH)));

        assertEquals(2, suyo.enviados.size());
        assertTrue(suyo.enviados.get(1).contains("event:" + NotificadorSesiones.EVENTO_NOTIFICACION));
        assertTrue(suyo.enviados.get(1).contains("Pago aprobado")); // el emisor de prueba recibe el objeto; Jackson lo serializa al enviar
        assertEquals(1, ajeno.enviados.size());
        assertTrue(!suyo.completado);
        assertTrue(cortado.completado);
        assertEquals(2, notificador.abiertas());
    }

    @Test
    void cerrarLaSesionAvisaATodosSusCanales() {
        EmisorDePrueba emisor = new EmisorDePrueba();
        notificador.suscribir(1, "jti", emisor);

        notificador.alTerminarSesion(new SesionTerminada(1, null, "cerrada"));

        assertTrue(emisor.completado);
        assertEquals(0, notificador.abiertas());
    }

    @Test
    void reconectarLaMismaSesionCierraElCanalAnterior() {
        EmisorDePrueba primero = new EmisorDePrueba();
        EmisorDePrueba segundo = new EmisorDePrueba();
        notificador.suscribir(1, "jti", primero);

        notificador.suscribir(1, "jti", segundo);

        assertTrue(primero.completado);
        assertEquals(1, notificador.abiertas());
    }

    @Test
    void elLatidoMantieneLosCanalesVivosYDescartaLosMuertos() {
        EmisorDePrueba vivo = new EmisorDePrueba();
        EmisorDePrueba cortado = new EmisorDePrueba(null, true);
        EmisorDePrueba vencido = new EmisorDePrueba(new IllegalStateException("ya completado"), false);
        notificador.suscribir(1, "a", vivo);
        notificador.suscribir(2, "b", cortado);
        notificador.suscribir(3, "c", vencido);

        notificador.latido();

        assertEquals(1, notificador.abiertas());
        assertTrue(vivo.enviados.get(1).contains(":latido"));
        assertTrue(cortado.completado);
        assertTrue(vencido.completado);
    }

    @Test
    void elConstructorPublicoCreaUnEmisorReal() {
        assertTrue(notificador.suscribir(1, "jti") instanceof SseEmitter);
        assertEquals(1_000L, notificador.suscribir(2, "otro").getTimeout());
    }
}
