package com.proyecto.servicios.repositorys.sf;

import com.proyecto.servicios.entity.sf.Cuenta;
import com.proyecto.servicios.models.response.CuentaResponse;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CuentaRepository extends JpaRepository<Cuenta, Integer>, JpaSpecificationExecutor<Cuenta> {

    List<Cuenta> findByClienteIdInOrderByIdAsc(Collection<Integer> ids);

    Optional<Cuenta> findByNumeroCuenta(String numeroCuenta);

    Optional<Cuenta> findByNumeroCuentaAndClienteId(String numeroCuenta, Integer clienteId);

    List<Cuenta> findByClienteIdOrderByIdAsc(Integer clienteId);

    @Query("""
            select new com.proyecto.servicios.models.response.CuentaResponse(
                c.id, c.cliente.id, c.numeroCuenta, c.saldo, c.estaActiva,
                c.fechaCreacion, c.fechaActualizacion)
            from Cuenta c
            where (:activa is null or c.estaActiva = :activa)
              and (:saldo is null or c.saldo = :saldo)
              and (:numero is null or c.numeroCuenta = :numero)
              and (:clienteId is null or c.cliente.id = :clienteId)
            """)
    Page<CuentaResponse> consultar(@Param("activa") Boolean activa,
            @Param("saldo") BigDecimal saldo, @Param("numero") String numeroCuenta,
            @Param("clienteId") Integer clienteId, Pageable pageable);

    @Query(value = "select lpad(nextval('numero_cuenta_seq')::text, 18, '0')", nativeQuery = true)
    String siguienteNumero();
}
