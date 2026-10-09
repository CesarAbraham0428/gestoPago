package com.proyecto.servicios.repositorys.sf;
import com.proyecto.servicios.entity.sf.Cuenta;
import org.springframework.data.jpa.repository.*;
import java.util.List;
import java.math.BigDecimal;
import org.springframework.data.domain.Page;
import com.proyecto.servicios.model.CuentaResumenResponse;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.query.Param;
import java.util.Optional;

public interface CuentaRepository extends JpaRepository<Cuenta,Integer>, JpaSpecificationExecutor<Cuenta> {
    List<Cuenta> findByClienteIdInOrderByIdAsc(java.util.Collection<Integer> ids);
    Optional<Cuenta> findByNumeroCuenta(String numeroCuenta);
    List<Cuenta> findByClienteIdOrderByIdAsc(Integer clienteId);
    @Query("select new com.proyecto.servicios.model.CuentaResumenResponse(c.id, c.cliente.id, c.numeroCuenta, c.estaActiva) "
        + "from Cuenta c where (:activa is null or c.estaActiva = :activa)")
    Page<CuentaResumenResponse> consultarResumen(@Param("activa") Boolean activa, Pageable pageable);

    @Query("select c.saldo from Cuenta c where c.numeroCuenta = :numero")
    Optional<BigDecimal> findSaldoByNumeroCuenta(@Param("numero") String numero);

    @Query("select c.cliente.id from Cuenta c where c.numeroCuenta = :numero")
    Optional<Integer> findClienteIdByNumeroCuenta(@Param("numero") String numero);
    @Query(value="select lpad(nextval('numero_cuenta_seq')::text, 18, '0')", nativeQuery=true)
    String siguienteNumero();
}
