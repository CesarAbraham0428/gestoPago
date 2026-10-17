package com.proyecto.servicios.repositorys.sf;
import com.proyecto.servicios.entity.sf.Usuario;
import com.proyecto.servicios.models.response.UsuarioResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.*;
import java.util.Optional;

public interface UsuarioRepository extends JpaRepository<Usuario,Integer> {
    @Query("""
        select new com.proyecto.servicios.models.response.UsuarioResponse(
            u.id, c.id, u.correo, u.activo, u.fechaCreacion, u.fechaActualizacion)
        from Usuario u join u.cliente c
        where (:propietarioId is null or c.id = :propietarioId)
          and (:rfc is null or upper(c.rfc) = :rfc)
          and (:curp is null or upper(c.curp) = :curp)
          and (:correo is null or lower(u.correo) = :correo)
          and (:activo is null or u.activo = :activo)
          and (:nombre is null or lower(c.primerNombre) like :nombre
               or lower(c.segundoNombre) like :nombre
               or lower(c.apellidoPaterno) like :nombre
               or lower(c.apellidoMaterno) like :nombre)
        """)
    Page<UsuarioResponse> consultar(
            @Param("rfc") String rfc, @Param("curp") String curp,
            @Param("correo") String correo, @Param("nombre") String nombre,
            @Param("activo") Boolean activo, @Param("propietarioId") Integer propietarioId,
            Pageable pageable);

    java.util.List<Usuario> findByClienteIdIn(java.util.Collection<Integer> ids);
    Optional<Usuario> findByClienteId(Integer id);
    @EntityGraph(attributePaths="cliente")
    @Query("select u from Usuario u where lower(u.correo) = lower(:correo)")
    Optional<Usuario> findByCorreoIgnoreCase(@org.springframework.data.repository.query.Param("correo") String correo);
    boolean existsByIdAndActivoTrueAndClienteActivoTrue(Integer id);
}
