package com.proyecto.servicios.service.Impl;

import com.proyecto.servicios.client.GestoPagoAuthException;
import com.proyecto.servicios.client.GestoPagoPagoException;
import com.proyecto.servicios.client.GestoPagoPagosClient;
import com.proyecto.servicios.entity.gestopago.GestoPagoToken;
import com.proyecto.servicios.entity.gestopago.Producto;
import com.proyecto.servicios.entity.sf.Pago;
import com.proyecto.servicios.models.request.PagoServicioRequest;
import com.proyecto.servicios.models.request.gestopago.GestoPagoSendTxRequest;
import com.proyecto.servicios.models.response.PagoServicioResponse;
import com.proyecto.servicios.models.response.gestopago.GestoPagoPagosResponse;
import com.proyecto.servicios.repositorys.gestopago.ProductoRepository;
import com.proyecto.servicios.service.GestoPagoTokenService;
import com.proyecto.servicios.service.PagoRegistroService;
import com.proyecto.servicios.service.PagoService;
import com.proyecto.servicios.service.exception.NegocioException;
import com.proyecto.servicios.service.exception.ValidacionException;
import com.proyecto.servicios.utils.CadenaAleatoria;
import com.proyecto.servicios.utils.FirmaGestoPago;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionException;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

/**
 * Flujo del pago de servicios:
 * 1. Generar la petición (validar catálogo, cuenta, saldo y PIN; retener saldo).
 * 2. Pagar servicio: serializar la petición a JSON.
 * 3. Firmar la petición (AES-256).
 * 4. Obtener el token vigente de GestoPago.
 * 5. Enviar a sendTx.do y validar que CODIGO sea 01.
 * Si no hay respuesta, confirmTx.do resuelve el pago (ver {@link #conciliarPendientes()}).
 */
@Service
@Slf4j
public class PagoServiceImpl implements PagoService {

    private static final DateTimeFormatter HORA_LOCAL = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final int LONGITUD_UPC = 15;

    // Reglas de getProductList / sendTx.
    private static final int TIPO_FRONT_RECARGA = 1;
    private static final int TIPO_FRONT_SERVICIO = 2;
    private static final Set<Integer> TIPOS_SERVICIO_CANJEABLES = Set.of(10, 11);
    private static final String TELEFONO_SERVICIO = "1111111111";
    private static final String TELEFONO_CANJEABLE = "#1111111111";

    // confirmTx no debe invocarse antes de 62 s después de sendTx y solo es válido durante 24 h.
    private static final Duration ESPERA_CONFIRMACION = Duration.ofSeconds(62);
    private static final Duration VIGENCIA_CONFIRMACION = Duration.ofHours(24);
    private static final Duration ESPERA_PENDIENTE_ABANDONADO = Duration.ofMinutes(5);

    private final ProductoRepository productos;
    private final PagoRegistroService registro;
    private final GestoPagoPagosClient pagosClient;
    private final GestoPagoTokenService tokenService;
    private final FirmaGestoPago firma;
    private final Integer idDistribuidor;
    private final String codigoDispositivo;
    private final String unidad;
    private final Clock clock;

    @Autowired
    public PagoServiceImpl(ProductoRepository productos, PagoRegistroService registro,
            GestoPagoPagosClient pagosClient, GestoPagoTokenService tokenService, FirmaGestoPago firma,
            @Value("${gestopago.auth.id-distribuidor}") Integer idDistribuidor,
            @Value("${gestopago.auth.codigo-dispositivo}") String codigoDispositivo,
            @Value("${gestopago.pagos.unidad}") String unidad,
            @Value("${gestopago.pagos.zona:America/Mexico_City}") String zona) {
        this(productos, registro, pagosClient, tokenService, firma, idDistribuidor, codigoDispositivo,
                unidad, Clock.system(ZoneId.of(zona)));
    }

    PagoServiceImpl(ProductoRepository productos, PagoRegistroService registro,
            GestoPagoPagosClient pagosClient, GestoPagoTokenService tokenService, FirmaGestoPago firma,
            Integer idDistribuidor, String codigoDispositivo, String unidad, Clock clock) {
        if (unidad == null || unidad.strip().length() < 3 || unidad.strip().length() > 25) {
            throw new IllegalStateException("gestopago.pagos.unidad debe medir de 3 a 25 caracteres");
        }
        this.productos = productos;
        this.registro = registro;
        this.pagosClient = pagosClient;
        this.tokenService = tokenService;
        this.firma = firma;
        this.idDistribuidor = idDistribuidor;
        this.codigoDispositivo = codigoDispositivo;
        this.unidad = unidad.strip();
        this.clock = clock;
    }

    @Override
    public PagoServicioResponse pagar(PagoServicioRequest request, Integer usuarioAutenticadoId) {
        Producto producto = productos.findFirstByIdProductoAndIdServicio(request.idProducto(), request.idServicio())
                .orElseThrow(() -> new NegocioException(HttpStatus.NOT_FOUND,
                        "El producto o servicio no existe en el catálogo"));
        Destino destino = resolverDestino(producto, request);

        String upc = CadenaAleatoria.generar(LONGITUD_UPC);
        String peticion = firma.serializar(new GestoPagoSendTxRequest(producto.getIdProducto(),
                producto.getIdServicio(), destino.telefono(), LocalDateTime.now(clock).format(HORA_LOCAL),
                destino.referencia(), destino.montoPago(), upc, unidad));

        Pago pago = registro.reservar(request, producto, destino.telefono(), destino.referencia(), upc,
                peticion, usuarioAutenticadoId);
        log.info("Saldo retenido para el pago: folio={}, upc={}", pago.getId(), upc);

        GestoPagoPagosResponse respuesta = enviar(pago, peticion);

        if (respuesta.esExitoso()) {
            return aprobar(pago, respuesta);
        }
        if (respuesta.esCodigo(GestoPagoPagosResponse.CODIGO_TIEMPO_AGOTADO)) {
            throw dejarEnRevision(pago, "GestoPago agotó el tiempo de espera (82)", respuesta.getRespuestaOriginal());
        }
        registro.revertir(pago.getId(), respuesta, respuesta.getMensaje());
        log.warn("GestoPago rechazó el pago; saldo devuelto: folio={}, codigo={}", pago.getId(), respuesta.getCodigo());
        if (respuesta.esSaldoInsuficiente()) {
            // Es el saldo del distribuidor en GestoPago: no se expone al cliente, pero hay que recargarlo.
            log.error("Saldo insuficiente del distribuidor en GestoPago: folio={}", pago.getId());
            throw new NegocioException(HttpStatus.SERVICE_UNAVAILABLE,
                    "El servicio no está disponible por el momento; no se realizó ningún cargo");
        }
        if (respuesta.esCodigo(GestoPagoPagosResponse.CODIGO_DUPLICADO)) {
            throw new NegocioException(HttpStatus.CONFLICT, "Pago duplicado: ya se aplicó un pago igual "
                    + "recientemente; no se realizó ningún cargo" + detalle(respuesta));
        }
        throw new NegocioException(HttpStatus.UNPROCESSABLE_ENTITY,
                "El proveedor rechazó el pago (código " + respuesta.getCodigo() + ")" + detalle(respuesta));
    }

    /**
     * GestoPago obliga a llamar a confirmTx cuando sendTx no devolvió resultado, con exactamente los
     * mismos parámetros, al menos 62 s después y dentro de las 24 h siguientes.
     */
    @Override
    public void conciliarPendientes() {
        Instant ahora = clock.instant();
        List<Integer> ids = registro.idsPorConfirmar(ahora.minus(VIGENCIA_CONFIRMACION),
                ahora.minus(ESPERA_CONFIRMACION), ahora.minus(ESPERA_PENDIENTE_ABANDONADO));
        for (Integer id : ids) {
            try {
                confirmar(id);
            } catch (RuntimeException e) {
                log.error("No se pudo confirmar el pago con GestoPago: folio={}, tipo={}",
                        id, e.getClass().getSimpleName());
            }
        }
    }

    private void confirmar(Integer pagoId) {
        Pago pago = registro.buscarParaConfirmar(pagoId);
        if (pago.getPeticion() == null) {
            log.warn("El pago no tiene la petición original; requiere revisión manual: folio={}", pagoId);
            return;
        }
        String firmada = firma.firmarJson(pago.getPeticion());
        GestoPagoPagosResponse respuesta = conRenovacion(token -> pagosClient.confirmar(token, firmada));

        if (respuesta.esExitoso() || (respuesta.esCodigo(GestoPagoPagosResponse.CODIGO_CONFIRMADO_APLICADO)
                && autorizacionValida(respuesta.getNumAutorizacion()))) {
            registro.confirmarAplicado(pagoId, respuesta);
            log.info("confirmTx: la transacción sí se aplicó: folio={}, idTx={}", pagoId, respuesta.getIdTx());
        } else if (respuesta.esCodigo(GestoPagoPagosResponse.CODIGO_CONFIRMADO_NO_APLICADO)) {
            registro.confirmarNoAplicado(pagoId, respuesta);
            log.info("confirmTx: la transacción no se aplicó; saldo devuelto: folio={}", pagoId);
        } else {
            log.warn("confirmTx sin resultado concluyente; se reintentará: folio={}, codigo={}",
                    pagoId, respuesta.getCodigo());
        }
    }

    private GestoPagoPagosResponse enviar(Pago pago, String peticion) {
        try {
            String peticionFirmada = firma.firmarJson(peticion);
            return conRenovacion(token -> pagosClient.enviar(token, peticionFirmada));
        } catch (GestoPagoPagoException e) {
            if (e.isResultadoIncierto()) {
                throw dejarEnRevision(pago, e.getMessage(), null);
            }
            registro.revertir(pago.getId(), null, e.getMessage());
            throw new NegocioException(HttpStatus.BAD_GATEWAY,
                    "No fue posible comunicarse con el proveedor de servicios; no se realizó ningún cargo");
        } catch (RuntimeException e) {
            // Fallas antes de enviar (token, firma, configuración): GestoPago nunca recibió el pago.
            registro.revertir(pago.getId(), null, "No enviado: " + e.getClass().getSimpleName());
            throw e;
        }
    }

    // No se sabe si GestoPago cobró: devolver el saldo podría regalar el pago. confirmTx lo resolverá.
    private NegocioException dejarEnRevision(Pago pago, String motivo, String respuestaOriginal) {
        registro.marcarEnRevision(pago.getId(), motivo, respuestaOriginal);
        log.error("Pago sin confirmación de GestoPago; queda EN_REVISION: folio={}, upc={}",
                pago.getId(), pago.getUpc());
        return new NegocioException(HttpStatus.GATEWAY_TIMEOUT, "No se recibió confirmación del proveedor. "
                + "El pago con folio " + pago.getId() + " quedó en revisión y el monto permanece retenido; "
                + "se confirmará automáticamente en unos minutos. No lo intentes de nuevo.");
    }

    private GestoPagoPagosResponse conRenovacion(Function<String, GestoPagoPagosResponse> llamada) {
        try {
            return llamada.apply(tokenActivo());
        } catch (GestoPagoPagoException e) {
            if (e.getTipo() != GestoPagoPagoException.Tipo.AUTENTICACION) {
                throw e;
            }
            // Token vencido (403): la transacción no se procesó y es seguro reintentar con el mismo UPC.
            log.warn("GestoPago rechazó el token; se renovará y se reintentará una vez");
            GestoPagoToken renovado = tokenService.renovarToken();
            if (renovado == null || renovado.getToken() == null || renovado.getToken().isBlank()) {
                throw new GestoPagoAuthException("No fue posible renovar la autenticación de GestoPago",
                        GestoPagoAuthException.Tipo.RESPUESTA_INVALIDA, null, e);
            }
            return llamada.apply(renovado.getToken());
        }
    }

    private String tokenActivo() {
        return tokenService.obtenerTokenActivo(idDistribuidor, codigoDispositivo)
                .map(GestoPagoToken::getToken)
                .filter(token -> !token.isBlank())
                .orElseThrow(() -> new GestoPagoAuthException("No hay un token activo de GestoPago",
                        GestoPagoAuthException.Tipo.RESPUESTA_INVALIDA, null, null));
    }

    private PagoServicioResponse aprobar(Pago pago, GestoPagoPagosResponse respuesta) {
        try {
            PagoServicioResponse comprobante = registro.aprobar(pago.getId(), respuesta);
            log.info("Pago aprobado por GestoPago: folio={}, idTx={}", pago.getId(), respuesta.getIdTx());
            return comprobante;
        } catch (DataAccessException | TransactionException e) {
            // GestoPago ya cobró: se responde éxito para que el cliente no repita el pago.
            log.error("Pago aprobado por GestoPago pero no se pudo actualizar su estado; conciliar: folio={}, idTx={}",
                    pago.getId(), respuesta.getIdTx());
            return registro.comprobante(pago, respuesta, null);
        }
    }

    /**
     * tipoFront 1 (recargas): el teléfono es el único dato y el precio del catálogo es el precio final.
     * tipoFront 2 (pagos de servicio): referencia y montoPago obligatorios, teléfono fijo "1111111111";
     * aquí el precio del catálogo es la comisión, no el monto.
     * idCatTipoServicio 10 y 11 (artículos canjeables, p. ej. tarjetas de regalo): teléfono fijo "#1111111111".
     */
    private Destino resolverDestino(Producto producto, PagoServicioRequest request) {
        Integer tipoFront = producto.getTipoFront();
        boolean canjeable = producto.getIdCatTipoServicio() != null
                && TIPOS_SERVICIO_CANJEABLES.contains(producto.getIdCatTipoServicio());
        if (tipoFront != null && tipoFront == TIPO_FRONT_RECARGA) {
            validarPrecioFijo(producto, request.monto());
            if (canjeable) {
                return new Destino(TELEFONO_CANJEABLE, null, null);
            }
            if (request.telefono() == null) {
                throw new ValidacionException("telefono: este producto requiere un número de teléfono de 10 dígitos");
            }
            return new Destino(request.telefono(), null, null);
        }
        if (tipoFront != null && tipoFront == TIPO_FRONT_SERVICIO) {
            if (request.referencia() == null) {
                throw new ValidacionException("referencia: este servicio requiere la referencia del recibo");
            }
            return new Destino(canjeable ? TELEFONO_CANJEABLE : TELEFONO_SERVICIO, request.referencia(),
                    request.monto());
        }
        throw new ValidacionException("idProducto: este producto no se puede pagar (tipoFront " + tipoFront + ")");
    }

    private void validarPrecioFijo(Producto producto, BigDecimal monto) {
        BigDecimal precio = producto.getPrecio();
        if (precio != null && precio.signum() > 0 && precio.compareTo(monto) != 0) {
            throw new ValidacionException("monto: el producto tiene un precio fijo de " + precio.toPlainString());
        }
    }

    private boolean autorizacionValida(String numAutorizacion) {
        return numAutorizacion != null && !numAutorizacion.equals("0") && !numAutorizacion.equals("-1");
    }

    private String detalle(GestoPagoPagosResponse respuesta) {
        return respuesta.getMensaje() == null ? "" : ": " + respuesta.getMensaje().replaceAll("\\s+", " ");
    }

    private record Destino(String telefono, String referencia, BigDecimal montoPago) {
    }
}
