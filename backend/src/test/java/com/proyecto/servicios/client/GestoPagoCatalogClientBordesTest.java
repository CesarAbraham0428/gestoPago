package com.proyecto.servicios.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.proyecto.servicios.client.GestoPagoCatalogException.Tipo;
import feign.Request;
import feign.Response;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Casos límite de la lectura de la respuesta del catálogo (cuerpo ausente, vacío, ilegible o con basura). */
class GestoPagoCatalogClientBordesTest {

    private final GestoPagoCatalogFeignClient feign = mock(GestoPagoCatalogFeignClient.class);
    private final GestoPagoCatalogClient client = new GestoPagoCatalogClient(feign);

    private static Request request() {
        return Request.create(Request.HttpMethod.GET, "https://gestopago.test/catalog", Map.of(), null,
                StandardCharsets.UTF_8);
    }

    private static Response.Builder base(int status) {
        return Response.builder().status(status).reason("x").request(request());
    }

    private Tipo tipoDelFallo() {
        return assertThrows(GestoPagoCatalogException.class, () -> client.consultarCatalogo("t")).getTipo();
    }

    @Test
    void unaRespuestaSinCuerpoSeTrataComoVacia() {
        when(feign.consultarCatalogo("Bearer t")).thenReturn(base(200).build());

        assertEquals(Tipo.RESPUESTA_VACIA, tipoDelFallo());
    }

    @Test
    void unCuerpoDeCeroBytesSeTrataComoVacio() {
        when(feign.consultarCatalogo("Bearer t")).thenReturn(base(200).body("", StandardCharsets.UTF_8).build());

        assertEquals(Tipo.RESPUESTA_VACIA, tipoDelFallo());
    }

    @Test
    void unCuerpoQueFallaAlLeerseEsUnErrorDeComunicacion() {
        Response.Body cuerpoRoto = new Response.Body() {
            @Override
            public Integer length() {
                return null;
            }

            @Override
            public boolean isRepeatable() {
                return false;
            }

            @Override
            public InputStream asInputStream() {
                return new InputStream() {
                    @Override
                    public int read() throws IOException {
                        throw new IOException("conexión cortada");
                    }
                };
            }

            @Override
            public Reader asReader(java.nio.charset.Charset charset) {
                return null;
            }

            @Override
            public void close() {
                // nada que cerrar
            }
        };
        when(feign.consultarCatalogo("Bearer t")).thenReturn(base(200).body(cuerpoRoto).build());

        GestoPagoCatalogException error =
                assertThrows(GestoPagoCatalogException.class, () -> client.consultarCatalogo("t"));

        assertEquals(Tipo.COMUNICACION, error.getTipo());
        assertEquals(200, error.getStatusHttp());
    }

    @Test
    void contenidoMalFormadoDespuesDeLaRaizEsXmlInvalido() {
        when(feign.consultarCatalogo("Bearer t")).thenReturn(base(200)
                .body("<RESPONSE><MENSAJE><CODIGO>01</CODIGO></MENSAJE></RESPONSE><basura>", StandardCharsets.UTF_8)
                .build());

        assertEquals(Tipo.XML, tipoDelFallo());
    }

    @Test
    void unaRaizDistintaSeRechazaComoXmlInvalido() {
        when(feign.consultarCatalogo("Bearer t")).thenReturn(base(200)
                .body("<OTRA/>", StandardCharsets.UTF_8).build());

        assertEquals(Tipo.XML, tipoDelFallo());
    }

    @Test
    void siNoSePuedeCrearElContextoJaxbFallaElArranque() {
        assertThrows(IllegalStateException.class, () -> GestoPagoCatalogClient.crearContexto(Runnable.class));
    }
}
