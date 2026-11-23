package com.proyecto.servicios.service.Impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.proyecto.servicios.client.GestoPagoAuthException;
import com.proyecto.servicios.client.GestoPagoCatalogClient;
import com.proyecto.servicios.client.GestoPagoCatalogException;
import com.proyecto.servicios.entity.gestopago.GestoPagoToken;
import com.proyecto.servicios.mapper.ProductoMapper;
import com.proyecto.servicios.models.response.CatalogoProductosResponse;
import com.proyecto.servicios.models.response.gestopago.GestoPagoCatalogMessage;
import com.proyecto.servicios.models.response.gestopago.GestoPagoCatalogResponse;
import com.proyecto.servicios.models.response.gestopago.GestoPagoProduct;
import com.proyecto.servicios.service.GestoPagoTokenService;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.springframework.dao.DataAccessResourceFailureException;

class ConsultorCatalogoGestoPagoTest {

    private final GestoPagoCatalogClient cliente = mock(GestoPagoCatalogClient.class);
    private final GestoPagoTokenService tokens = mock(GestoPagoTokenService.class);
    private final ConsultorCatalogoGestoPago consultor = new ConsultorCatalogoGestoPago(cliente, tokens,
            Mappers.getMapper(ProductoMapper.class), 42, "device");

    private static GestoPagoToken token(String valor) {
        GestoPagoToken token = new GestoPagoToken();
        token.setToken(valor);
        return token;
    }

    private static GestoPagoCatalogResponse catalogo(String codigo, GestoPagoProduct... productos) {
        GestoPagoCatalogMessage mensaje = new GestoPagoCatalogMessage();
        mensaje.setCodigo(codigo);
        GestoPagoCatalogResponse respuesta = new GestoPagoCatalogResponse();
        respuesta.setMensaje(mensaje);
        respuesta.setProductos(List.of(productos));
        return respuesta;
    }

    private static GestoPagoProduct producto(String nombre) {
        GestoPagoProduct producto = new GestoPagoProduct();
        producto.setProducto(nombre);
        producto.setServicio("Servicio");
        producto.setIdServicio(10);
        producto.setIdProducto(20);
        return producto;
    }

    private static GestoPagoCatalogException autenticacion() {
        return new GestoPagoCatalogException("rechazado", GestoPagoCatalogException.Tipo.AUTENTICACION, 401, null);
    }

    @Test
    void unCatalogoValidoSeDevuelveConCodigoCero() {
        when(tokens.obtenerTokenActivo(42, "device")).thenReturn(Optional.of(token("t")));
        when(cliente.consultarCatalogo("t")).thenReturn(catalogo("01", producto("Telcel")));

        CatalogoProductosResponse respuesta = consultor.consultar();

        assertEquals(0, respuesta.getCodigo());
        assertEquals(1, respuesta.getTotal());
    }

    @Test
    void sinTokenUtilizableNoSeConsultaAlProveedor() {
        when(tokens.obtenerTokenActivo(42, "device")).thenReturn(Optional.empty());
        assertEquals(1, consultor.consultar().getCodigo());

        when(tokens.obtenerTokenActivo(42, "device")).thenReturn(Optional.of(token(" ")));
        assertEquals(1, consultor.consultar().getCodigo());

        when(tokens.obtenerTokenActivo(42, "device")).thenReturn(Optional.of(token(null)));
        assertEquals(1, consultor.consultar().getCodigo());
        verify(cliente, never()).consultarCatalogo(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void unaRespuestaIncompletaOInvalidaDelProveedorEsCodigoUno() {
        when(tokens.obtenerTokenActivo(42, "device")).thenReturn(Optional.of(token("t")));
        GestoPagoCatalogResponse sinMensaje = new GestoPagoCatalogResponse();
        GestoPagoCatalogResponse sinProductos = catalogo("01");
        sinProductos.setProductos(null);
        GestoPagoCatalogResponse productoIncompleto = catalogo("01", new GestoPagoProduct());

        when(cliente.consultarCatalogo("t")).thenReturn(null, sinMensaje, catalogo("99", producto("x")),
                sinProductos, productoIncompleto, catalogo("01"));

        for (int i = 0; i < 6; i++) {
            assertEquals(1, consultor.consultar().getCodigo(), "caso " + i);
        }
    }

    @Test
    void unRechazoDeAutenticacionRenuevaElTokenYReintentaUnaVez() {
        when(tokens.obtenerTokenActivo(42, "device")).thenReturn(Optional.of(token("viejo")));
        when(cliente.consultarCatalogo("viejo")).thenThrow(autenticacion());
        when(tokens.renovarToken()).thenReturn(token("nuevo"));
        when(cliente.consultarCatalogo("nuevo")).thenReturn(catalogo("01", producto("Telcel")));

        assertEquals(0, consultor.consultar().getCodigo());
    }

    @Test
    void siLaRenovacionNoTraeTokenElResultadoEsCodigoUnoConElMensajeDelFallo() {
        when(tokens.obtenerTokenActivo(42, "device")).thenReturn(Optional.of(token("viejo")));
        when(cliente.consultarCatalogo("viejo")).thenThrow(autenticacion());
        when(tokens.renovarToken()).thenReturn(token(" "));

        CatalogoProductosResponse respuesta = consultor.consultar();

        assertEquals(1, respuesta.getCodigo());
        assertEquals("No fue posible renovar la autenticación de GestoPago", respuesta.getMensaje());
        verify(cliente, never()).consultarCatalogo("nuevo");
    }

    @Test
    void otrosFallosDelProveedorSeReportanSinRenovar() {
        when(tokens.obtenerTokenActivo(42, "device")).thenReturn(Optional.of(token("t")));
        when(cliente.consultarCatalogo("t")).thenThrow(new GestoPagoCatalogException("caído"));

        CatalogoProductosResponse respuesta = consultor.consultar();

        assertEquals(1, respuesta.getCodigo());
        assertEquals("caído", respuesta.getMensaje());
        verify(tokens, never()).renovarToken();
    }

    @Test
    void unFalloDeAutenticacionAlObtenerElTokenEsCodigoUno() {
        when(tokens.obtenerTokenActivo(42, "device")).thenThrow(
                new GestoPagoAuthException("sin credenciales", GestoPagoAuthException.Tipo.AUTENTICACION, 401, null));

        assertEquals(1, consultor.consultar().getCodigo());
    }

    @Test
    void unFalloDeBaseDeDatosAlObtenerElTokenEsCodigoDos() {
        when(tokens.obtenerTokenActivo(42, "device")).thenThrow(new DataAccessResourceFailureException("bd"));

        CatalogoProductosResponse respuesta = consultor.consultar();

        assertEquals(2, respuesta.getCodigo());
        assertTrue(respuesta.getProductos().isEmpty());
    }

    @Test
    void siLaRenovacionDevuelveUnTokenNuloElResultadoEsCodigoUno() {
        when(tokens.obtenerTokenActivo(42, "device")).thenReturn(Optional.of(token("viejo")));
        when(cliente.consultarCatalogo("viejo")).thenThrow(autenticacion());
        when(tokens.renovarToken()).thenReturn(token(null));

        assertEquals(1, consultor.consultar().getCodigo());
    }
}
