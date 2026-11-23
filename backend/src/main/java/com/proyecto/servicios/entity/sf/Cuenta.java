package com.proyecto.servicios.entity.sf;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.Instant;
@Entity @Table(name="cuentas") @Getter @Setter
public class Cuenta extends Auditado {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Integer id;
    @ManyToOne(fetch=FetchType.LAZY, optional=false)
    @JoinColumn(name="cliente_id", nullable=false, updatable=false) private Cliente cliente;
    @Column(name="numero_cuenta", nullable=false, unique=true, length=18, updatable=false) private String numeroCuenta;
    @Column(nullable=false, precision=18, scale=2) private BigDecimal saldo = BigDecimal.ZERO;
    @Column(name="esta_activa", nullable=false) private boolean estaActiva = true;
    @Column(name="pin_hash", length=60) private String pinHash;
    @Column(name="intentos_pin_fallidos", nullable=false) private int intentosPinFallidos;
    @Column(name="pin_bloqueado_hasta") private Instant pinBloqueadoHasta;
}
