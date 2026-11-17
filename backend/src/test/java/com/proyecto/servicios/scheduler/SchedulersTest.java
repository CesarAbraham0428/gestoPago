package com.proyecto.servicios.scheduler;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.proyecto.servicios.client.GestoPagoAuthException;
import com.proyecto.servicios.service.ConciliacionPagoService;
import com.proyecto.servicios.service.GestoPagoTokenService;
import com.proyecto.servicios.service.ProductoService;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.transaction.CannotCreateTransactionException;

class SchedulersTest {

    @Test
    void elCatalogoSeSincronizaYUnFalloNoSePropaga() {
        ProductoService productos = mock(ProductoService.class);
        CatalogoProductosScheduler scheduler = new CatalogoProductosScheduler(productos);

        scheduler.sincronizarCatalogoDiariamente();
        verify(productos).sincronizarCatalogo();

        doThrow(new IllegalStateException("boom")).when(productos).sincronizarCatalogo();
        assertDoesNotThrow(scheduler::sincronizarCatalogoDiariamente);
    }

    @Test
    void laConciliacionSeEjecutaYUnFalloNoSePropaga() {
        ConciliacionPagoService conciliacion = mock(ConciliacionPagoService.class);
        PagoConciliacionScheduler scheduler = new PagoConciliacionScheduler(conciliacion);

        scheduler.conciliarPagosPendientes();
        verify(conciliacion).conciliarPendientes();

        doThrow(new IllegalStateException("boom")).when(conciliacion).conciliarPendientes();
        assertDoesNotThrow(scheduler::conciliarPagosPendientes);
    }

    @Test
    void elTokenSeRevisaYSeRenuevaCadaDia() {
        GestoPagoTokenService tokens = mock(GestoPagoTokenService.class);
        GestoPagoTokenScheduler scheduler = new GestoPagoTokenScheduler(tokens);

        scheduler.revisarRenovacion();
        scheduler.renovarDiariamente();

        verify(tokens).renovarSiHaceFalta();
        verify(tokens).renovarToken();
    }

    @Test
    void losFallosDeGestoPagoOdeBaseDeDatosNoDetienenElProgramador() {
        GestoPagoTokenService tokens = mock(GestoPagoTokenService.class);
        GestoPagoTokenScheduler scheduler = new GestoPagoTokenScheduler(tokens);
        doThrow(new GestoPagoAuthException("x", GestoPagoAuthException.Tipo.HTTP, 500, null))
                .when(tokens).renovarSiHaceFalta();
        doThrow(new CannotCreateTransactionException("bd")).when(tokens).renovarToken();

        assertDoesNotThrow(scheduler::revisarRenovacion);
        assertDoesNotThrow(scheduler::renovarDiariamente);

        doThrow(new DataAccessResourceFailureException("bd")).when(tokens).renovarSiHaceFalta();
        assertDoesNotThrow(scheduler::revisarRenovacion);
    }
}
