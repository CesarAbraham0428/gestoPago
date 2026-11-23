package com.proyecto.servicios.service;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Canal Server-Sent Events por sesión: avisa al dispositivo en cuanto su sesión se cierra o la reemplaza otro
 * inicio de sesión, para que la app salga al instante sin consultar periódicamente.
 *
 * shortcut: las conexiones viven en la memoria de esta instancia; con varias instancias de la API, publicar
 * {@link SesionTerminada} por Redis pub/sub para que cada instancia avise a las suyas.
 */
@Service
public class NotificadorSesiones {

    public static final String EVENTO_CIERRE = "sesion_cerrada";

    /** Lo publica {@link SesionService}: terminaron las sesiones del usuario distintas de {@code jtiVigente}. */
    public record SesionTerminada(Integer usuarioId, String jtiVigente, String motivo) { }

    private record Suscripcion(Integer usuarioId, String jti, SseEmitter emitter) { }

    private final Set<Suscripcion> suscripciones = ConcurrentHashMap.newKeySet();
    private final long duracionMs;

    public NotificadorSesiones(@Value("${gestopago.app.sesion.eventos-duracion-ms:600000}") long duracionMs) {
        this.duracionMs = duracionMs;
    }

    /** Abre el canal de la sesión {@code jti}; una reconexión de la misma sesión reemplaza al canal anterior. */
    public SseEmitter suscribir(Integer usuarioId, String jti) {
        return suscribir(usuarioId, jti, new SseEmitter(duracionMs));
    }

    SseEmitter suscribir(Integer usuarioId, String jti, SseEmitter emitter) {
        suscripciones.removeIf(s -> {
            boolean misma = s.jti().equals(jti);
            if (misma) {
                s.emitter().complete();
            }
            return misma;
        });
        Suscripcion nueva = new Suscripcion(usuarioId, jti, emitter);
        suscripciones.add(nueva);
        // Primer evento: el cliente recibe las cabeceras de inmediato y sabe que el canal quedó abierto.
        enviar(nueva, SseEmitter.event().name("conectado").data("ok"));
        return emitter;
    }

    /** Se ejecuta después de confirmar la transacción que cerró o reemplazó la sesión, nunca antes. */
    @TransactionalEventListener(fallbackExecution = true)
    public void alTerminarSesion(SesionTerminada evento) {
        suscripciones.removeIf(s -> {
            boolean afectada = s.usuarioId().equals(evento.usuarioId()) && !s.jti().equals(evento.jtiVigente());
            if (afectada) {
                enviar(s, SseEmitter.event().name(EVENTO_CIERRE).data(evento.motivo()));
                s.emitter().complete();
            }
            return afectada;
        });
    }

    /** Latido para que proxies y balanceadores no corten un canal sin tráfico; descarta los canales muertos. */
    @Scheduled(fixedRateString = "${gestopago.app.sesion.latido-ms:25000}")
    public void latido() {
        suscripciones.removeIf(s -> !enviar(s, SseEmitter.event().comment("latido")));
    }

    int abiertas() {
        return suscripciones.size();
    }

    /** @return false si el canal ya estaba cerrado o se perdió la conexión. */
    private static boolean enviar(Suscripcion s, SseEmitter.SseEventBuilder evento) {
        try {
            s.emitter().send(evento);
            return true;
        } catch (IOException | IllegalStateException ex) {
            s.emitter().completeWithError(ex);
            return false;
        }
    }
}
