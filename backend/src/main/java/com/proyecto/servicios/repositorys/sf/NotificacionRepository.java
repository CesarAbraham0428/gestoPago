package com.proyecto.servicios.repositorys.sf;

import com.proyecto.servicios.entity.sf.Notificacion;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificacionRepository extends JpaRepository<Notificacion, Integer> {

    Page<Notificacion> findByUsuarioId(Integer usuarioId, Pageable pageable);

    long countByUsuarioIdAndLeidaFalse(Integer usuarioId);

    @Modifying
    @Query("update Notificacion n set n.leida = true where n.usuarioId = :usuarioId and n.id = :id")
    int marcarLeida(@Param("usuarioId") Integer usuarioId, @Param("id") Integer id);

    @Modifying
    @Query("update Notificacion n set n.leida = true where n.usuarioId = :usuarioId and n.leida = false")
    int marcarTodasLeidas(@Param("usuarioId") Integer usuarioId);
}
