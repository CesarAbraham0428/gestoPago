package com.proyecto.servicios.entity.sf;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Primer paso del acceso superado (contraseña correcta); queda pendiente el código TOTP del segundo factor. */
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

    @Column(nullable = false)
    private int intentos;

    /** El código ya fue aceptado; se reintenta solo la confirmación de cerrar otra sesión. */
    @Column(nullable = false)
    private boolean verificado;

    /** Paso TOTP del código aceptado. */
    @Column(nullable = false)
    private long paso;

    @Column(name = "creado_en", nullable = false, updatable = false)
    private Instant creadoEn;

    @Column(name = "expira_en", nullable = false, updatable = false)
    private Instant expiraEn;
}
