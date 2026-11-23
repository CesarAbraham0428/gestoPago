package com.proyecto.servicios.repositorys.sf;

import com.proyecto.servicios.entity.sf.DesafioAutenticacion;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DesafioAutenticacionRepository extends JpaRepository<DesafioAutenticacion, String> {

    Optional<DesafioAutenticacion> findByUsuarioId(Integer usuarioId);

    /** Bloquea la fila: dos verificaciones simultáneas no pueden saltarse el límite de intentos. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from DesafioAutenticacion d where d.id = :id")
    Optional<DesafioAutenticacion> bloquearPorId(@Param("id") String id);

    @Modifying
    @Query("delete from DesafioAutenticacion d where d.usuarioId = :usuarioId")
    void eliminarDeUsuario(@Param("usuarioId") Integer usuarioId);
}
