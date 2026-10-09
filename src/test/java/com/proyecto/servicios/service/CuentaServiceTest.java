package com.proyecto.servicios.service;

import com.proyecto.servicios.repositorys.sf.*;
import com.proyecto.servicios.service.exception.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import com.proyecto.servicios.entity.sf.Cuenta;
import com.proyecto.servicios.model.CuentaResumenResponse;
import org.mockito.ArgumentMatchers;
import java.util.List;
import java.math.BigDecimal;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class CuentaServiceTest {
    private final CuentaRepository cuentas = mock(CuentaRepository.class);
    private final CuentaService service = new CuentaService(cuentas, mock(ClientesRepository.class), mock(EntityManager.class));

    @Test void saldoNoCargaLaEntidadCompleta() {
        when(cuentas.findSaldoByNumeroCuenta("000000000000000001")).thenReturn(Optional.of(BigDecimal.ZERO));
        assertEquals(BigDecimal.ZERO, service.saldo("000000000000000001"));
        verify(cuentas, never()).findByNumeroCuenta(anyString());
    }

    @Test void saldoDeCuentaInexistenteDevuelveExcepcionPersonalizada() {
        when(cuentas.findSaldoByNumeroCuenta("inexistente")).thenReturn(Optional.empty());
        assertThrows(CuentaNoEncontradaException.class, () -> service.saldo("inexistente"));
    }

    @Test void rechazaPaginacionInvalidaAntesDeConsultar() {
        assertThrows(ValidacionException.class, () -> service.consultar(true, -1, 20));
        assertThrows(ValidacionException.class, () -> service.consultar(true, 0, 0));
        assertThrows(ValidacionException.class, () -> service.consultar(true, 0, 101));
        verifyNoInteractions(cuentas);
    }

    @Test void paginaGlobalUsaResumen() {
        var resumen = new CuentaResumenResponse(4, 7, "000000000000000001", true);
        when(cuentas.consultarResumen(isNull(), any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of(resumen), PageRequest.of(2, 10), 21));
        var resultado = service.consultar(null, 2, 10);
        assertEquals(21, resultado.getTotalElements());
        assertEquals(2, resultado.getNumber());
        assertEquals(resumen, resultado.getContent().get(0));
        verify(cuentas).consultarResumen(isNull(), argThat(p -> p.getPageNumber() == 2 && p.getPageSize() == 10));
        verify(cuentas, never()).findAll();
    }

    @Test void filtroSinCoincidenciasDevuelve404() {
        when(cuentas.findAll(ArgumentMatchers.<Specification<Cuenta>>any(), any(Pageable.class)))
            .thenReturn(Page.empty());
        assertThrows(CuentaNoEncontradaException.class, () -> service.consultar(true, 0, 10));
    }

    @Test void idInexistenteDevuelve404() {
        when(cuentas.findById(123)).thenReturn(Optional.empty());
        assertThrows(CuentaNoEncontradaException.class, () -> service.obtenerPorId(123));
    }
}
