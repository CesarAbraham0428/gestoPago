package com.proyecto.servicios.repositorys.sf;
import com.proyecto.servicios.entity.sf.Cuenta;
import org.springframework.data.jpa.repository.*;
import java.util.List;
import java.util.Optional;
public interface CuentaRepository extends JpaRepository<Cuenta,Integer> {
    Optional<Cuenta> findByNumeroCuenta(String numeroCuenta);
    List<Cuenta> findByClienteIdOrderByIdAsc(Integer clienteId);
    List<Cuenta> findByEstaActiva(boolean activa);
    @Query(value="select lpad(nextval('numero_cuenta_seq')::text, 18, '0')", nativeQuery=true)
    String siguienteNumero();
}
