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
    @Column(name="password_hash", nullable=false, length=60) private String passwordHash;
    @Column(nullable=false) private boolean activo = true;
    /** Secreto TOTP de la app autenticadora; se guarda cifrado (AES-GCM). */
    @Convert(converter=com.proyecto.servicios.utils.CifradoTokenConverter.class)
    @Column(name="totp_secreto", length=255) private String totpSecreto;
    /** Verdadero cuando el usuario ya demostró con un código que su app tiene el secreto. */
    @Column(name="totp_confirmado", nullable=false) private boolean totpConfirmado;
    /** Último paso de 30 s aceptado: un código no se puede usar dos veces. */
    @Column(name="totp_ultimo_paso", nullable=false) private long totpUltimoPaso;
}
