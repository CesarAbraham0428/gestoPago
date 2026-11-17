package com.proyecto.servicios.client;

import com.proyecto.servicios.models.response.gestopago.GestoPagoPagosResponse;
import com.proyecto.servicios.utils.CadenaAleatoria;
import feign.FeignException;
import feign.Response;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Envía la transacción firmada a sendTx.do, la confirma con confirmTx.do y consulta adeudos con
 * verifyReference.do. Solo orquesta la llamada: la interpretación de la respuesta es de
 * {@link RespuestaPagoParser} y la clasificación de fallos, de {@link ErroresGestoPagoPagos}.
 */
@Component
@Slf4j
public class GestoPagoPagosClient {

    private final GestoPagoPagosFeignClient feignClient;
    private final RespuestaPagoParser parser;
    private final String parametroFirma;

    GestoPagoPagosClient(GestoPagoPagosFeignClient feignClient, RespuestaPagoParser parser,
            @Value("${gestopago.pagos.parametro-firma:signed}") String parametroFirma) {
        this.feignClient = feignClient;
        this.parser = parser;
        this.parametroFirma = parametroFirma;
    }

    public GestoPagoPagosResponse enviar(String token, String peticionFirmada) {
        return ejecutar("sendTx", () -> feignClient.enviarTransaccion("Bearer " + token,
                formulario(peticionFirmada)));
    }

    /** confirmTx.do: se invoca con los mismos parámetros de sendTx cuando no hubo respuesta. */
    public GestoPagoPagosResponse confirmar(String token, String peticionFirmada) {
        return ejecutar("confirmTx", () -> feignClient.confirmarTransaccion("Bearer " + token,
                formulario(peticionFirmada)));
    }

    /** verifyReference.do: consulta el adeudo de una referencia (SALDO mayor a 0 es el adeudo). No es una venta. */
    public GestoPagoPagosResponse verificarReferencia(String token, String peticionFirmada) {
        return ejecutar("verifyReference", () -> feignClient.verificarReferencia("Bearer " + token,
                formulario(peticionFirmada)));
    }

    private Map<String, String> formulario(String peticionFirmada) {
        // "random" es una cadena aleatoria; la documentación sugiere el timestamp actual.
        return Map.of("random", System.currentTimeMillis() + "_" + CadenaAleatoria.generar(6),
                parametroFirma, peticionFirmada);
    }

    private GestoPagoPagosResponse ejecutar(String operacion, Supplier<Response> llamada) {
        long inicio = System.nanoTime();
        Integer statusHttp = null;
        log.info("Inicia {} en GestoPago", operacion);
        try {
            Response response = llamada.get();
            statusHttp = response.status();
            return leer(response, statusHttp);
        } catch (FeignException e) {
            GestoPagoPagoException error = ErroresGestoPagoPagos.traducirFeign(e);
            statusHttp = error.getStatusHttp();
            registrarError(error, inicio);
            throw error;
        } catch (IOException e) {
            GestoPagoPagoException error = ErroresGestoPagoPagos.respuestaInvalida(
                    "No se pudo leer la respuesta de GestoPago", statusHttp, e);
            registrarError(error, inicio);
            throw error;
        } catch (GestoPagoPagoException e) {
            registrarError(e, inicio);
            throw e;
        } finally {
            log.info("Finaliza {} en GestoPago: statusHttp={}, duraciónMs={}",
                    operacion, statusHttp, duracionMs(inicio));
        }
    }

    private GestoPagoPagosResponse leer(Response response, int statusHttp) throws IOException {
        try (response) {
            if (statusHttp < 200 || statusHttp >= 300) {
                throw ErroresGestoPagoPagos.errorHttp(statusHttp, null);
            }
            if (response.body() == null) {
                throw ErroresGestoPagoPagos.respuestaInvalida("GestoPago devolvió una respuesta vacía", statusHttp,
                        null);
            }
            try (InputStream cuerpo = response.body().asInputStream()) {
                return parser.interpretar(new String(cuerpo.readAllBytes(), StandardCharsets.UTF_8), statusHttp);
            }
        }
    }

    private void registrarError(GestoPagoPagoException e, long inicio) {
        // No se registra el mensaje de Feign: puede incluir cabeceras (token) o el cuerpo firmado.
        log.error("Falló el envío de transacción a GestoPago: tipo={}, statusHttp={}, incierto={}, duraciónMs={}",
                e.getTipo(), e.getStatusHttp(), e.isResultadoIncierto(), duracionMs(inicio));
    }

    private long duracionMs(long inicio) {
        return Duration.ofNanos(System.nanoTime() - inicio).toMillis();
    }
}
