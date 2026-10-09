package com.proyecto.servicios.repositorys.sf;
import com.proyecto.servicios.entity.sf.Usuario;
import org.springframework.data.jpa.repository.*;
import java.util.Optional;

public interface UsuarioRepository extends JpaRepository<Usuario,Integer> {
    java.util.List<Usuario> findByClienteIdIn(java.util.Collection<Integer> ids);
    Optional<Usuario> findByClienteId(Integer id);
    @EntityGraph(attributePaths="cliente")
    @Query("select u from Usuario u where lower(u.correo) = lower(:correo)")
    Optional<Usuario> findByCorreoIgnoreCase(@org.springframework.data.repository.query.Param("correo") String correo);
    boolean existsByIdAndActivoTrueAndClienteActivoTrue(Integer id);
}
