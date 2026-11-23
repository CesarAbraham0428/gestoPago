package com.proyecto.servicios.entity.sf;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.Getter;
import org.hibernate.annotations.Immutable;

/** Sesión activa de un usuario (a lo sumo una). Se escribe solo con consultas nativas atómicas. */
@Entity
@Table(name = "sesiones_usuario")
@Immutable
@Getter
public class SesionUsuario {
    @Id
    @Column(name = "usuario_id")
    private Integer usuarioId;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId
    @JoinColumn(name = "usuario_id")
    private Usuario usuario;

    @Column(nullable = false, length = 36)
    private String jti;

    @Column(name = "creada_en", nullable = false)
    private Instant creadaEn;

    @Column(name = "expira_en", nullable = false)
    private Instant expiraEn;
}
