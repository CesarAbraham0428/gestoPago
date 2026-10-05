package com.proyecto.servicios.repositorys.sf;
import com.proyecto.servicios.entity.sf.Usuario;
import org.springframework.data.jpa.repository.*;
import java.util.Optional;
public interface UsuarioRepository extends JpaRepository<Usuario,Integer> {
    @Override @EntityGraph(attributePaths="cliente") Optional<Usuario> findById(Integer id);
    @EntityGraph(attributePaths="cliente") Optional<Usuario> findByCorreoIgnoreCase(String correo);
    Optional<Usuario> findByClienteId(Integer clienteId);
}
