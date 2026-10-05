package com.proyecto.servicios.entity.sf;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
@Entity @Table(name="usuarios") @Getter @Setter
public class Usuario extends Auditado {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Integer id;
    @OneToOne(fetch=FetchType.LAZY, optional=false)
    @JoinColumn(name="cliente_id", nullable=false, unique=true, updatable=false) private Cliente cliente;
    @Column(nullable=false, length=100) private String correo;
    @Column(name="password_hash", nullable=false, length=250) private String passwordHash;
    @Column(nullable=false) private boolean activo = true;
}
