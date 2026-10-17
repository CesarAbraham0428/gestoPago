package com.proyecto.servicios.entity.sf;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
@Entity @Table(name="cuentas") @Getter @Setter
public class Cuenta extends Auditado {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Integer id;
    @ManyToOne(fetch=FetchType.LAZY, optional=false)
    @JoinColumn(name="cliente_id", nullable=false, updatable=false) private Cliente cliente;
    @Column(name="numero_cuenta", nullable=false, unique=true, length=18, updatable=false) private String numeroCuenta;
    @Column(nullable=false, precision=18, scale=2) private BigDecimal saldo = BigDecimal.ZERO;
    @Column(name="esta_activa", nullable=false) private boolean estaActiva = true;
}
