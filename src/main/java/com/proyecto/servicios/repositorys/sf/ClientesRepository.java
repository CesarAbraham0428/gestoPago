package com.proyecto.servicios.repositorys.sf;
import com.proyecto.servicios.entity.sf.Cliente;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import java.util.Optional;
public interface ClientesRepository extends JpaRepository<Cliente,Integer>, JpaSpecificationExecutor<Cliente> {
    boolean existsByCurp(String curp);
    boolean existsByRfc(String rfc);
    boolean existsByCorreoElectronicoIgnoreCase(String correo);
    boolean existsByCorreoElectronicoIgnoreCaseAndIdNot(String correo, Integer id);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Cliente c where c.id = :id")
    Optional<Cliente> bloquearPorId(@org.springframework.data.repository.query.Param("id") Integer id);
}
