package com.proyecto.servicios.service;

import com.proyecto.servicios.entity.gestopago.Producto;
import com.proyecto.servicios.entity.sf.Cuenta;
import com.proyecto.servicios.entity.sf.EstadoPago;
import com.proyecto.servicios.entity.sf.Pago;
import com.proyecto.servicios.models.request.PagoServicioRequest;
import com.proyecto.servicios.models.response.PagoServicioResponse;
import com.proyecto.servicios.models.response.gestopago.GestoPagoPagosResponse;
import com.proyecto.servicios.repositorys.sf.CuentaRepository;
import com.proyecto.servicios.repositorys.sf.PagoRepository;
import com.proyecto.servicios.service.exception.CuentaNoEncontradaException;
import com.proyecto.servicios.service.exception.NegocioException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Pasos transaccionales del pago. Cada método es una transacción corta para no mantener
 * bloqueada la cuenta mientras se espera la respuesta de GestoPago.
 *
 * Estrategia de saldo: se retiene (descuenta) ANTES de enviar el pago. Así dos pagos simultáneos
 * no pueden gastar el mismo saldo. Si GestoPago rechaza, se devuelve; si no hay certeza, queda
 * retenido en EN_REVISION hasta que confirmTx diga si se aplicó.
 *
 * Cada petición y respuesta se guarda en la tabla pagos: GestoPago exige ese log de evidencia.
 */
@Service
public class PagoRegistroService {

    private static final int LONGITUD_MENSAJE = 250;

    private final CuentaRepository cuentas;
    private final PagoRepository pagos;
    private final AutorizacionService autorizacion;
    private final PasswordEncoder passwordEncoder;

    public PagoRegistroService(CuentaRepository cuentas, PagoRepository pagos,
            AutorizacionService autorizacion, PasswordEncoder passwordEncoder) {
        this.cuentas = cuentas;
        this.pagos = pagos;
        this.autorizacion = autorizacion;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public Pago reservar(PagoServicioRequest request, Producto producto, String telefono, String referencia,
            String upc, String peticion, Integer usuarioAutenticadoId) {
        Cuenta cuenta = cuentas.bloquearPorNumero(request.numeroCuenta())
                .orElseThrow(CuentaNoEncontradaException::new);
        autorizacion.autorizarCliente(cuenta.getCliente().getId(), usuarioAutenticadoId);

        if (!cuenta.isEstaActiva()) {
            throw new NegocioException(HttpStatus.CONFLICT, "La cuenta no está activa");
        }
        if (cuenta.getSaldo() == null || cuenta.getSaldo().signum() <= 0) {
            throw new NegocioException(HttpStatus.UNPROCESSABLE_ENTITY, "La cuenta no tiene saldo disponible");
        }
        if (cuenta.getSaldo().compareTo(request.monto()) < 0) {
            throw new NegocioException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Saldo insuficiente para realizar el pago");
        }
        validarPin(cuenta, request.pinAutorizacion());

        cuenta.setSaldo(cuenta.getSaldo().subtract(request.monto()));

        Pago pago = new Pago();
        pago.setCuenta(cuenta);
        pago.setUsuarioId(usuarioAutenticadoId);
        pago.setIdProducto(producto.getIdProducto());
        pago.setIdServicio(producto.getIdServicio());
        pago.setTipoFront(producto.getTipoFront());
        pago.setTelefono(telefono);
        pago.setReferencia(referencia);
        pago.setMonto(request.monto());
        pago.setUpc(upc);
        pago.setPeticion(peticion);
        pago.setEstado(EstadoPago.PENDIENTE);
        return pagos.saveAndFlush(pago);
    }

    @Transactional
    public PagoServicioResponse aprobar(Integer pagoId, GestoPagoPagosResponse respuesta) {
        Pago pago = buscar(pagoId);
        pago.setRespuesta(respuesta.getRespuestaOriginal());
        marcarAprobado(pago, respuesta);
        return comprobante(pago, respuesta, pago.getCuenta().getSaldo());
    }

    /** Devuelve el saldo retenido. Es idempotente: un pago ya cerrado no se vuelve a revertir. */
    @Transactional
    public void revertir(Integer pagoId, GestoPagoPagosResponse respuesta, String motivo) {
        Pago pago = buscar(pagoId);
        if (respuesta != null) {
            pago.setRespuesta(respuesta.getRespuestaOriginal());
        }
        devolverSaldo(pago, respuesta == null ? null : respuesta.getCodigo(), motivo);
    }

    @Transactional
    public void marcarEnRevision(Integer pagoId, String motivo, String respuestaOriginal) {
        Pago pago = buscar(pagoId);
        if (respuestaOriginal != null) {
            pago.setRespuesta(respuestaOriginal);
        }
        if (pago.getEstado() == EstadoPago.PENDIENTE) {
            pago.setEstado(EstadoPago.EN_REVISION);
            pago.setMensaje(recortar(motivo, LONGITUD_MENSAJE));
        }
    }

    /** confirmTx respondió que la transacción sí se aplicó: el cargo queda definitivo. */
    @Transactional
    public void confirmarAplicado(Integer pagoId, GestoPagoPagosResponse respuesta) {
        Pago pago = buscar(pagoId);
        pago.setRespuestaConfirmacion(respuesta.getRespuestaOriginal());
        if (pago.getEstado() != EstadoPago.RECHAZADO) {
            marcarAprobado(pago, respuesta);
        }
    }

    /** confirmTx respondió que la transacción no se aplicó: se devuelve el saldo. */
    @Transactional
    public void confirmarNoAplicado(Integer pagoId, GestoPagoPagosResponse respuesta) {
        Pago pago = buscar(pagoId);
        pago.setRespuestaConfirmacion(respuesta.getRespuestaOriginal());
        devolverSaldo(pago, respuesta.getCodigo(), respuesta.getMensaje());
    }

    @Transactional(readOnly = true)
    public Pago buscarParaConfirmar(Integer pagoId) {
        return buscar(pagoId);
    }

    @Transactional(readOnly = true)
    public List<Integer> idsPorConfirmar(Instant vigencia, Instant limiteRevision, Instant limitePendiente) {
        return pagos.idsPorConfirmar(vigencia, limiteRevision, limitePendiente);
    }

    public PagoServicioResponse comprobante(Pago pago, GestoPagoPagosResponse respuesta,
            BigDecimal saldoDisponible) {
        return new PagoServicioResponse(EstadoPago.APROBADO.name(), respuesta.getIdTx(),
                respuesta.getNumAutorizacion(), pago.getMonto(), pago.getReferencia(), saldoDisponible,
                respuesta.getFecha(), respuesta.getPin(), respuesta.getLegend());
    }

    private void marcarAprobado(Pago pago, GestoPagoPagosResponse respuesta) {
        pago.setEstado(EstadoPago.APROBADO);
        pago.setCodigoRespuesta(recortar(respuesta.getCodigo(), 4));
        pago.setIdTx(respuesta.getIdTx());
        pago.setNumAutorizacion(recortar(respuesta.getNumAutorizacion(), 60));
        pago.setComision(respuesta.getComision());
        String mensaje = respuesta.getLegend() != null ? respuesta.getLegend() : respuesta.getMensaje();
        pago.setMensaje(recortar(mensaje, LONGITUD_MENSAJE));
    }

    private void devolverSaldo(Pago pago, String codigoRespuesta, String motivo) {
        if (pago.getEstado() == EstadoPago.APROBADO || pago.getEstado() == EstadoPago.RECHAZADO) {
            return;
        }
        Cuenta cuenta = cuentas.bloquearPorId(pago.getCuenta().getId())
                .orElseThrow(CuentaNoEncontradaException::new);
        cuenta.setSaldo(cuenta.getSaldo().add(pago.getMonto()));
        pago.setEstado(EstadoPago.RECHAZADO);
        pago.setCodigoRespuesta(recortar(codigoRespuesta, 4));
        pago.setMensaje(recortar(motivo, LONGITUD_MENSAJE));
    }

    private void validarPin(Cuenta cuenta, String pin) {
        if (cuenta.getPinHash() == null) {
            throw new NegocioException(HttpStatus.CONFLICT,
                    "La cuenta no tiene un PIN de autorización configurado");
        }
        if (pin == null || !passwordEncoder.matches(pin, cuenta.getPinHash())) {
            throw new NegocioException(HttpStatus.FORBIDDEN, "PIN de autorización incorrecto");
        }
    }

    private Pago buscar(Integer pagoId) {
        return pagos.findById(pagoId)
                .orElseThrow(() -> new IllegalStateException("No existe el pago " + pagoId));
    }

    private String recortar(String texto, int longitud) {
        return texto == null || texto.length() <= longitud ? texto : texto.substring(0, longitud);
    }
}
