package com.proyecto.servicios.repositorys.sf;
import com.proyecto.servicios.entity.sf.Domicilio;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
public interface DomicilioRepository extends JpaRepository<Domicilio,Integer> {
    Optional<Domicilio> findByClienteId(Integer clienteId);
}
