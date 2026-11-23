package com.proyecto.servicios.service.Impl;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.proyecto.servicios.entity.sf.Cliente;
import com.proyecto.servicios.models.request.ClienteConsultaRequest;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.domain.Specification;

@SuppressWarnings({"unchecked", "rawtypes"})
class ClienteConsultaSpecificationTest {

    private final Root<Cliente> root = mock(Root.class, RETURNS_DEEP_STUBS);
    private final CriteriaQuery<?> query = mock(CriteriaQuery.class, RETURNS_DEEP_STUBS);
    private final CriteriaBuilder builder = mock(CriteriaBuilder.class, RETURNS_DEEP_STUBS);

    private void aplicar(ClienteConsultaRequest request, Integer propietarioId) {
        Specification<Cliente> especificacion = ClienteConsultaSpecification.de(request, propietarioId);
        especificacion.toPredicate(root, query, builder);
    }

    @Test
    void sinFiltrosNoAgregaNingunaRestriccion() {
        aplicar(new ClienteConsultaRequest(), null);

        verify(builder).and(new Predicate[0]);
        verify(builder, never()).equal(any(), any(Object.class));
    }

    @Test
    void limitaAlPropietarioCuandoNoEsAdministrador() {
        aplicar(new ClienteConsultaRequest(), 7);

        verify(builder).equal(any(), eq(7));
    }

    @Test
    void normalizaIdentidadesYCorreoYBuscaElNombreEnLosCuatroCampos() {
        ClienteConsultaRequest request = new ClienteConsultaRequest();
        request.setRfc(" pera900101ab1 ");
        request.setCurp(" pera900101mdfrzn01 ");
        request.setNombre(" ANA ");
        request.setCorreo(" Ana@Example.COM ");

        aplicar(request, null);

        verify(builder).equal(any(), eq("PERA900101AB1"));
        verify(builder).equal(any(), eq("PERA900101MDFRZN01"));
        verify(builder).equal(any(), eq("ana@example.com"));
        verify(builder, org.mockito.Mockito.times(4)).like(any(), eq("%ana%"));
        verify(builder).or(any(Predicate[].class));
    }

    @Test
    void filtraPorCuentaEstadoYRangoDeFechasEnLaZonaDeMexico() {
        ClienteConsultaRequest request = new ClienteConsultaRequest();
        request.setNumeroCuenta("000000000000000001");
        request.setActivo(true);
        request.setDesde(LocalDate.of(2026, 1, 1));
        request.setHasta(LocalDate.of(2026, 1, 31));

        aplicar(request, null);

        ZoneId zona = ZoneId.of("America/Mexico_City");
        verify(builder).equal(any(), eq("000000000000000001"));
        verify(builder).equal(any(), eq(true));
        verify(builder).greaterThanOrEqualTo(any(), eq(LocalDate.of(2026, 1, 1).atStartOfDay(zona).toInstant()));
        verify(builder).lessThan(any(), eq(LocalDate.of(2026, 2, 1).atStartOfDay(zona).toInstant()));
        verify(builder, never()).like(any(), anyString());
    }
}
