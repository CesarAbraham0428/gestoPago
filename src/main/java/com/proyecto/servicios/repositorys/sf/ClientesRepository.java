package com.proyecto.servicios.repositorys.sf;
import com.proyecto.servicios.entity.sf.Cliente;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import java.util.Optional;

public interface ClientesRepository extends JpaRepository<Cliente,Integer>, JpaSpecificationExecutor<Cliente> {
    boolean existsByCurp(String curp);
    boolean existsByRfc(String rfc);
    @Query("select (count(c) > 0) from Cliente c where lower(c.correoElectronico) = lower(:correo)")
    boolean existsByCorreoElectronicoIgnoreCase(@org.springframework.data.repository.query.Param("correo") String correo);
    @Query("select (count(c) > 0) from Cliente c where lower(c.correoElectronico) = lower(:correo) and c.id <> :id")
    boolean existsByCorreoElectronicoIgnoreCaseAndIdNot(
        @org.springframework.data.repository.query.Param("correo") String correo,
        @org.springframework.data.repository.query.Param("id") Integer id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Cliente c where c.id = :id")
    Optional<Cliente> bloquearPorId(@org.springframework.data.repository.query.Param("id") Integer id);
}
