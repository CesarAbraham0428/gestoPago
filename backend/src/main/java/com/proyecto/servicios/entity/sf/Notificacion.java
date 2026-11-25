package com.proyecto.servicios.entity.sf;
import jakarta.persistence.*;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;
@Entity @Table(name="notificaciones") @Getter @Setter
public class Notificacion {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Integer id;
    @Column(name="usuario_id", nullable=false, updatable=false) private Integer usuarioId;
    @Enumerated(EnumType.STRING) @Column(nullable=false, length=30, updatable=false) private TipoNotificacion tipo;
    @Column(nullable=false, length=100, updatable=false) private String titulo;
    @Column(nullable=false, length=250, updatable=false) private String mensaje;
    @Column(length=15, updatable=false) private String upc;
    @Column(nullable=false) private boolean leida;
    @Column(name="fecha_creacion", nullable=false, updatable=false) private Instant fecha = Instant.now();
}
