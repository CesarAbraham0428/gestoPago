package com.proyecto.servicios.service;

import com.proyecto.servicios.entity.sf.Notificacion;
import com.proyecto.servicios.entity.sf.Pago;
import com.proyecto.servicios.entity.sf.TipoNotificacion;
import com.proyecto.servicios.models.response.ListaGenericResponse;
import com.proyecto.servicios.models.response.NotificacionResponse;
import com.proyecto.servicios.models.response.NotificacionesResponse;
import com.proyecto.servicios.repositorys.sf.NotificacionRepository;
import java.math.BigDecimal;
import java.util.Locale;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Bandeja de notificaciones. Se escribe en la misma transacción que el cambio que la origina y, una vez
 * confirmada, {@link NotificadorSesiones} la empuja por SSE al dispositivo conectado.
 *
 * shortcut: sin purga ni caducidad de notificaciones antiguas; añadir un job de limpieza cuando la tabla crezca.
 */
@Service
public class NotificacionService {

    /** Se publica al guardar; el notificador la envía por SSE solo después del commit. */
    public record NotificacionCreada(Integer usuarioId, NotificacionResponse notificacion) { }

    private final NotificacionRepository repositorio;
    private final ApplicationEventPublisher eventos;

    public NotificacionService(NotificacionRepository repositorio, ApplicationEventPublisher eventos) {
        this.repositorio = repositorio;
        this.eventos = eventos;
    }

    /** Registra el aviso del estado en que acaba de quedar el pago. Cuenta y destino van enmascarados. */
    @Transactional
    public void registrarPago(Pago pago, TipoNotificacion tipo) {
        String destino = ultimos(pago.getReferencia() != null ? pago.getReferencia() : pago.getTelefono());
        String detalle = "Cuenta ••••" + ultimos(pago.getCuenta().getNumeroCuenta())
                + (destino.isEmpty() ? "" : " · destino ••••" + destino);
        String titulo;
        String mensaje;
        switch (tipo) {
            case PAGO_APROBADO -> {
                titulo = "Pago aprobado";
                mensaje = "Se cobraron " + dinero(pago.cargo()) + " de tu cuenta. " + detalle
                        + ". Si no reconoces este pago, comunícate con soporte de inmediato.";
            }
            case PAGO_RECHAZADO -> {
                titulo = "Pago rechazado";
                mensaje = "No se aplicó el pago de " + dinero(pago.getMonto())
                        + "; el saldo se devolvió a tu cuenta. " + detalle + ".";
            }
            default -> {
                titulo = "Pago en revisión";
                mensaje = "Estamos confirmando el pago de " + dinero(pago.getMonto())
                        + " con el proveedor; el saldo está retenido. Te avisaremos el resultado. " + detalle + ".";
            }
        }
        Notificacion n = new Notificacion();
        n.setUsuarioId(pago.getUsuarioId());
        n.setTipo(tipo);
        n.setTitulo(titulo);
        n.setMensaje(mensaje);
        n.setUpc(pago.getUpc());
        eventos.publishEvent(new NotificacionCreada(pago.getUsuarioId(),
                NotificacionResponse.desde(repositorio.saveAndFlush(n))));
    }

    @Transactional(readOnly = true)
    public NotificacionesResponse listar(Integer usuarioId, int pagina, int tamanio) {
        var datos = repositorio.findByUsuarioId(usuarioId,
                PageRequest.of(pagina, tamanio, Sort.by(Sort.Direction.DESC, "id"))).map(NotificacionResponse::desde);
        return new NotificacionesResponse(ListaGenericResponse.desde(datos),
                repositorio.countByUsuarioIdAndLeidaFalse(usuarioId));
    }

    /** Idempotente; un id ajeno o inexistente no cambia nada (es indistinguible para quien llama). */
    @Transactional
    public void marcarLeida(Integer usuarioId, Integer id) {
        repositorio.marcarLeida(usuarioId, id);
    }

    @Transactional
    public void marcarTodasLeidas(Integer usuarioId) {
        repositorio.marcarTodasLeidas(usuarioId);
    }

    private static String dinero(BigDecimal monto) {
        return "$" + String.format(Locale.US, "%,.2f", monto);
    }

    private static String ultimos(String texto) {
        if (texto == null) {
            return "";
        }
        return texto.length() <= 4 ? texto : texto.substring(texto.length() - 4);
    }
}
