package com.proyecto.servicios.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.proyecto.servicios.entity.sf.Cuenta;
import com.proyecto.servicios.entity.sf.Notificacion;
import com.proyecto.servicios.entity.sf.Pago;
import com.proyecto.servicios.entity.sf.TipoNotificacion;
import com.proyecto.servicios.repositorys.sf.NotificacionRepository;
import com.proyecto.servicios.service.NotificacionService.NotificacionCreada;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

class NotificacionServiceTest {

    private final NotificacionRepository repositorio = mock(NotificacionRepository.class);
    private final ApplicationEventPublisher eventos = mock(ApplicationEventPublisher.class);
    private final NotificacionService service = new NotificacionService(repositorio, eventos);

    private Pago pago() {
        Cuenta cuenta = new Cuenta();
        cuenta.setNumeroCuenta("000000000000001234");
        Pago pago = new Pago();
        pago.setCuenta(cuenta);
        pago.setUsuarioId(7);
        pago.setUpc("UPC000000000001");
        pago.setMonto(new BigDecimal("1500.00"));
        pago.setComisionCliente(new BigDecimal("12.50"));
        pago.setReferencia("REFERENCIA9876");
        return pago;
    }

    private Notificacion registrar(Pago pago, TipoNotificacion tipo) {
        when(repositorio.saveAndFlush(any(Notificacion.class))).thenAnswer(inv -> {
            Notificacion n = inv.getArgument(0);
            n.setId(1);
            return n;
        });
        service.registrarPago(pago, tipo);
        ArgumentCaptor<Notificacion> guardada = ArgumentCaptor.forClass(Notificacion.class);
        verify(repositorio).saveAndFlush(guardada.capture());
        return guardada.getValue();
    }

    @Test
    void aprobadoCobraMontoMasComisionYEnmascaraCuentaYDestino() {
        Notificacion n = registrar(pago(), TipoNotificacion.PAGO_APROBADO);

        assertEquals("Pago aprobado", n.getTitulo());
        assertEquals(7, n.getUsuarioId());
        assertEquals("UPC000000000001", n.getUpc());
        assertTrue(n.getMensaje().contains("$1,512.50"));
        assertTrue(n.getMensaje().contains("••••1234"));
        assertTrue(n.getMensaje().contains("destino ••••9876"));
        assertFalse(n.getMensaje().contains("000000000000001234"));
        assertFalse(n.getMensaje().contains("REFERENCIA"));
        assertFalse(n.isLeida());
    }

    @Test
    void rechazadoAvisaQueElSaldoSeDevolvio() {
        Notificacion n = registrar(pago(), TipoNotificacion.PAGO_RECHAZADO);

        assertEquals("Pago rechazado", n.getTitulo());
        assertTrue(n.getMensaje().contains("saldo se devolvió"));
        assertTrue(n.getMensaje().contains("$1,500.00"));
    }

    @Test
    void enRevisionAvisaQueSeAvisaraElResultado() {
        Notificacion n = registrar(pago(), TipoNotificacion.PAGO_EN_REVISION);

        assertEquals("Pago en revisión", n.getTitulo());
        assertTrue(n.getMensaje().contains("saldo está retenido"));
    }

    @Test
    void publicaElEventoParaElCanalEnTiempoReal() {
        registrar(pago(), TipoNotificacion.PAGO_APROBADO);

        ArgumentCaptor<Object> evento = ArgumentCaptor.forClass(Object.class);
        verify(eventos).publishEvent(evento.capture());
        NotificacionCreada creada = (NotificacionCreada) evento.getValue();
        assertEquals(7, creada.usuarioId());
        assertEquals("PAGO_APROBADO", creada.notificacion().tipo());
    }

    @Test
    void sinDestinoNiReferenciaSoloMencionaLaCuenta() {
        Pago pago = pago();
        pago.setReferencia(null);

        Notificacion n = registrar(pago, TipoNotificacion.PAGO_APROBADO);

        assertFalse(n.getMensaje().contains("destino"));
    }

    @Test
    void listarDevuelveLaPaginaYElTotalSinLeer() {
        Notificacion n = new Notificacion();
        n.setId(3);
        n.setTipo(TipoNotificacion.PAGO_APROBADO);
        n.setTitulo("Pago aprobado");
        n.setMensaje("m");
        when(repositorio.findByUsuarioId(any(Integer.class), any(Pageable.class))).thenReturn(new PageImpl<>(List.of(n)));
        when(repositorio.countByUsuarioIdAndLeidaFalse(7)).thenReturn(4L);

        var respuesta = service.listar(7, 0, 20);

        assertEquals(4, respuesta.noLeidas());
        assertEquals(1, respuesta.lista().datos().size());
        assertEquals(3, respuesta.lista().datos().get(0).id());
    }

    @Test
    void marcarComoLeidasDelegaEnElRepositorioConElUsuarioDelToken() {
        service.marcarLeida(7, 3);
        service.marcarTodasLeidas(7);

        verify(repositorio).marcarLeida(7, 3);
        verify(repositorio).marcarTodasLeidas(7);
    }
}
