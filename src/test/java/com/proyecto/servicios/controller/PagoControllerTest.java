package com.proyecto.servicios.controller;

import com.proyecto.servicios.controller.advice.GlobalExceptionHandler;
import com.proyecto.servicios.models.response.PagoServicioResponse;
import com.proyecto.servicios.service.LimitePagosService;
import com.proyecto.servicios.service.PagoService;
import com.proyecto.servicios.service.exception.NegocioException;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class PagoControllerTest {

    private static final String PAGO = """
            {"numeroCuenta":"000000000000000001","idProducto":20,"idServicio":30,"monto":100.00,
             "telefono":"5512345678","pinAutorizacion":"1234"}""";
    private final PagoService service = mock(PagoService.class);
    private final LimitePagosService limite = mock(LimitePagosService.class);
    private final UsernamePasswordAuthenticationToken usuario =
            new UsernamePasswordAuthenticationToken("7", null, List.of());
    private MockMvc mvc;

    @BeforeEach
    void preparar() {
        mvc = MockMvcBuilders.standaloneSetup(new PagoController(service, limite))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @Test
    void pagoAprobadoDevuelveElComprobante() throws Exception {
        when(service.pagar(any(), eq(7))).thenReturn(new PagoServicioResponse("APROBADO", 291437188L,
                "100024988", new BigDecimal("100.00"), BigDecimal.ZERO, null, new BigDecimal("50.00"),
                "25/Feb/2022 18:15:25", null, null));

        pagar(PAGO).andExpect(status().isOk())
                .andExpect(jsonPath("datos.estado").value("APROBADO"))
                .andExpect(jsonPath("datos.numAutorizacion").value("100024988"))
                .andExpect(jsonPath("datos.saldoDisponible").value(50.00));

        verify(limite).verificar(7);
    }

    @Test
    void montoConMasDeDosDecimalesSeRechazaSinLlamarAlServicio() throws Exception {
        pagar(PAGO.replace("100.00", "100.001")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("mensaje", containsString("monto")));
        verifyNoInteractions(service);
    }

    @Test
    void faltaElPinSeRechaza() throws Exception {
        pagar(PAGO.replace(",\"pinAutorizacion\":\"1234\"", "")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("mensaje", containsString("pinAutorizacion")));
        verifyNoInteractions(service);
    }

    @Test
    void campoNoPermitidoSeRechaza() throws Exception {
        pagar(PAGO.replace("}", ",\"comision\":0}")).andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test
    void limiteExcedidoResponde429SinPagar() throws Exception {
        doThrow(new NegocioException(HttpStatus.TOO_MANY_REQUESTS, "Demasiadas solicitudes de pago"))
                .when(limite).verificar(7);

        pagar(PAGO).andExpect(status().isTooManyRequests());
        verifyNoInteractions(service);
    }

    @Test
    void pagoEnRevisionResponde504() throws Exception {
        when(service.pagar(any(), eq(7))).thenThrow(new NegocioException(HttpStatus.GATEWAY_TIMEOUT,
                "No se recibió confirmación del proveedor"));

        pagar(PAGO).andExpect(status().isGatewayTimeout())
                .andExpect(jsonPath("mensaje").value("No se recibió confirmación del proveedor"));
    }

    private ResultActions pagar(String cuerpo) throws Exception {
        return mvc.perform(post("/pagos").principal(usuario).contentType("application/json").content(cuerpo));
    }
}
