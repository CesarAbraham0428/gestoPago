package com.proyecto.servicios.repositorys.sf;
import com.proyecto.servicios.entity.sf.Domicilio;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.Collection;
import java.util.List;
public interface DomicilioRepository extends JpaRepository<Domicilio,Integer> {
    Optional<Domicilio> findByClienteId(Integer clienteId);
    List<Domicilio> findByClienteIdIn(Collection<Integer> clienteIds);
}
