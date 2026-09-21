package com.proyecto.servicios.repositorys.sf;

import com.proyecto.servicios.entity.sf.Registro;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RegistroRepository extends JpaRepository<Registro, Integer> {
    Optional<Registro> findByUsuarioIgnoreCase(String usuario);
    boolean existsByUsuarioIgnoreCase(String usuario);
}
