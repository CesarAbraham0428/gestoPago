package com.proyecto.servicios.service.Impl;

import com.proyecto.servicios.client.GestoPagoPagoException;
import com.proyecto.servicios.entity.gestopago.Producto;
import com.proyecto.servicios.entity.sf.EstadoPago;
import com.proyecto.servicios.entity.sf.Pago;
import com.proyecto.servicios.models.request.PagoServicioRequest;
import com.proyecto.servicios.models.request.gestopago.GestoPagoSendTxRequest;
import com.proyecto.servicios.models.response.ConsultaPagoResponse;
import com.proyecto.servicios.models.response.PagoServicioResponse;
import com.proyecto.servicios.models.response.gestopago.GestoPagoPagosResponse;
import com.proyecto.servicios.repositorys.gestopago.ProductoRepository;
import com.proyecto.servicios.service.DestinoPago;
import com.proyecto.servicios.service.GestoPagoPagosGateway;
import com.proyecto.servicios.service.PagoRegistroService;
import com.proyecto.servicios.service.PagoService;
import com.proyecto.servicios.service.ResolutorDestinoPago;
import com.proyecto.servicios.service.exception.NegocioException;
import com.proyecto.servicios.utils.CadenaAleatoria;
import com.proyecto.servicios.utils.FirmaGestoPago;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionException;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Flujo del pago de servicios:
 * 1. Generar la petición (validar catálogo, cuenta, saldo y PIN; retener saldo).
 * 2. Pagar servicio: serializar la petición a JSON.
 * 3. Firmar la petición (AES-256).
 * 4. Enviar a sendTx.do con el token vigente de GestoPago ({@link GestoPagoPagosGateway}) y validar que CODIGO sea 01.
 * Si no hay respuesta, confirmTx.do resuelve el pago (ver {@link com.proyecto.servicios.service.ConciliacionPagoService}).
 */
@Service
@Slf4j
public class PagoServiceImpl implements PagoService {

    private static final DateTimeFormatter HORA_LOCAL = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final int LONGITUD_UPC = 15;

    private final ProductoRepository productos;
    private final PagoRegistroService registro;
    private final GestoPagoPagosGateway gateway;
    private final ResolutorDestinoPago resolutor;
    private final FirmaGestoPago firma;
    private final String unidad;
    private final Clock clock;

    @Autowired
    public PagoServiceImpl(ProductoRepository productos, PagoRegistroService registro,
            GestoPagoPagosGateway gateway, ResolutorDestinoPago resolutor, FirmaGestoPago firma,
            @Value("${gestopago.pagos.unidad}") String unidad,
            @Value("${gestopago.pagos.zona:America/Mexico_City}") String zona) {
        this(productos, registro, gateway, resolutor, firma, unidad, Clock.system(ZoneId.of(zona)));
    }

    PagoServiceImpl(ProductoRepository productos, PagoRegistroService registro, GestoPagoPagosGateway gateway,
            ResolutorDestinoPago resolutor, FirmaGestoPago firma, String unidad, Clock clock) {
        if (unidad == null || unidad.strip().length() < 3 || unidad.strip().length() > 25) {
            throw new IllegalStateException("gestopago.pagos.unidad debe medir de 3 a 25 caracteres");
        }
        this.productos = productos;
        this.registro = registro;
        this.gateway = gateway;
        this.resolutor = resolutor;
        this.firma = firma;
        this.unidad = unidad.strip();
        this.clock = clock;
    }

    @Override
    public PagoServicioResponse pagar(PagoServicioRequest request, Integer usuarioAutenticadoId) {
        Producto producto = productos.findFirstByIdProductoAndIdServicio(request.idProducto(), request.idServicio())
                .orElseThrow(() -> new NegocioException(HttpStatus.NOT_FOUND,
                        "El producto o servicio no existe en el catálogo"));
        DestinoPago destino = resolutor.resolver(producto, request);

        String upc = CadenaAleatoria.generar(LONGITUD_UPC);
        String peticion = firma.serializar(new GestoPagoSendTxRequest(producto.getIdProducto(),
                producto.getIdServicio(), destino.telefono(), LocalDateTime.now(clock).format(HORA_LOCAL),
                destino.referencia(), destino.montoPago(), upc, unidad));

        Pago pago = registro.reservar(request, producto, destino, upc, peticion, usuarioAutenticadoId);
        log.info("Saldo retenido para el pago: folio={}, upc={}", pago.getId(), upc);

        GestoPagoPagosResponse respuesta = enviar(pago, peticion);

        if (respuesta.esExitoso()) {
            return aprobar(pago, respuesta);
        }
        if (respuesta.esCodigo(GestoPagoPagosResponse.CODIGO_TIEMPO_AGOTADO)) {
            throw dejarEnRevision(pago, "GestoPago agotó el tiempo de espera (82)", respuesta.getRespuestaOriginal());
        }
        throw rechazar(pago, respuesta);
    }

    @Override
    public ConsultaPagoResponse consultar(String upc, Integer usuarioAutenticadoId) {
        Pago pago = registro.buscarDeUsuario(upc, usuarioAutenticadoId)
                .orElseThrow(() -> new NegocioException(HttpStatus.NOT_FOUND, "No existe un pago con ese UPC"));
        boolean aprobado = pago.getEstado() == EstadoPago.APROBADO;
        return new ConsultaPagoResponse(pago.getUpc(), pago.getEstado().name(), mensajeDeEstado(pago.getEstado()),
                pago.getMonto(), pago.getComisionCliente(), pago.getReferencia(),
                aprobado ? pago.getIdTx() : null, aprobado ? pago.getNumAutorizacion() : null);
    }

    private String mensajeDeEstado(EstadoPago estado) {
        return switch (estado) {
            case APROBADO -> "El pago fue aplicado correctamente";
            case RECHAZADO -> "El pago no se realizó; no se realizó ningún cargo";
            case PENDIENTE, EN_REVISION -> "El pago está en proceso; el monto permanece retenido y se "
                    + "confirmará automáticamente en unos minutos. No lo intentes de nuevo";
        };
    }

    private GestoPagoPagosResponse enviar(Pago pago, String peticion) {
        try {
            return gateway.enviar(firma.firmarJson(peticion));
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
                + "El pago (referencia " + pago.getUpc() + ") quedó en revisión y el monto permanece retenido; "
                + "se confirmará automáticamente en unos minutos. No lo intentes de nuevo.");
    }

    // El texto de GestoPago solo se guarda en la BD; al cliente se le responde con mensajes propios.
    private NegocioException rechazar(Pago pago, GestoPagoPagosResponse respuesta) {
        registro.revertir(pago.getId(), respuesta, respuesta.getMensaje());
        log.warn("GestoPago rechazó el pago; saldo devuelto: folio={}, codigo={}", pago.getId(), respuesta.getCodigo());
        if (respuesta.esSaldoInsuficiente()) {
            // Es el saldo del distribuidor en GestoPago: no se expone al cliente, pero hay que recargarlo.
            log.error("Saldo insuficiente del distribuidor en GestoPago: folio={}", pago.getId());
            return new NegocioException(HttpStatus.SERVICE_UNAVAILABLE,
                    "El servicio no está disponible por el momento; no se realizó ningún cargo");
        }
        if (respuesta.esCodigo(GestoPagoPagosResponse.CODIGO_DUPLICADO)) {
            return new NegocioException(HttpStatus.CONFLICT, "Pago duplicado: ya se aplicó un pago igual "
                    + "recientemente; no se realizó ningún cargo");
        }
        return new NegocioException(HttpStatus.UNPROCESSABLE_ENTITY,
                "El proveedor rechazó el pago; no se realizó ningún cargo");
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
}
