package com.proyecto.servicios.entity.sf;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Código de verificación (segundo factor) pendiente de un usuario. Nunca guarda el código en claro. */
@Entity
@Table(name = "desafios_autenticacion")
@Getter
@Setter
@NoArgsConstructor
public class DesafioAutenticacion {
    @Id
    @Column(length = 64)
    private String id;

    @Column(name = "usuario_id", nullable = false, updatable = false)
    private Integer usuarioId;

    @Column(name = "codigo_hash", nullable = false, length = 64, updatable = false)
    private String codigoHash;

    @Column(nullable = false)
    private int intentos;

    @Column(name = "creado_en", nullable = false, updatable = false)
    private Instant creadoEn;

    @Column(name = "expira_en", nullable = false, updatable = false)
    private Instant expiraEn;
}
