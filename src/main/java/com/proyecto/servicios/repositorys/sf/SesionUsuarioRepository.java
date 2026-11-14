package com.proyecto.servicios.repositorys.sf;

import com.proyecto.servicios.entity.sf.SesionUsuario;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SesionUsuarioRepository extends JpaRepository<SesionUsuario, Integer> {

    /** Identificador de la sesión vigente si el usuario y su cliente siguen activos; una sola consulta por solicitud. */
    @Query("""
            select s.jti from SesionUsuario s
            where s.usuarioId = :usuarioId and s.expiraEn > :ahora
              and s.usuario.activo = true and s.usuario.cliente.activo = true
            """)
    Optional<String> jtiVigente(@Param("usuarioId") Integer usuarioId, @Param("ahora") Instant ahora);

    /**
     * Abre la sesión de forma atómica. Devuelve 0 si ya existe una sesión vigente y no se pidió
     * reemplazarla; así dos inicios de sesión simultáneos nunca dejan dos sesiones activas.
     */
    @Modifying
    @Query(value = """
            INSERT INTO sesiones_usuario (usuario_id, jti, creada_en, expira_en)
            VALUES (:usuarioId, :jti, :ahora, :expiraEn)
            ON CONFLICT (usuario_id) DO UPDATE
               SET jti = EXCLUDED.jti, creada_en = EXCLUDED.creada_en, expira_en = EXCLUDED.expira_en
             WHERE :reemplazar OR sesiones_usuario.expira_en <= :ahora
            """, nativeQuery = true)
    int abrir(@Param("usuarioId") Integer usuarioId, @Param("jti") String jti, @Param("ahora") Instant ahora,
            @Param("expiraEn") Instant expiraEn, @Param("reemplazar") boolean reemplazar);

    @Modifying
    @Query("delete from SesionUsuario s where s.usuarioId = :usuarioId")
    void cerrar(@Param("usuarioId") Integer usuarioId);
}
