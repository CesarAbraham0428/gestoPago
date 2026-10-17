package com.proyecto.servicios.entity.sf;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
@Entity @Table(name="domicilios") @Getter @Setter
public class Domicilio extends Auditado {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Integer id;
    @OneToOne(fetch=FetchType.LAZY, optional=false)
    @JoinColumn(name="cliente_id", nullable=false, unique=true, updatable=false) private Cliente cliente;
    @Column(nullable=false, columnDefinition="text") private String calle;
    @Column(name="numero_exterior", nullable=false, length=50) private String numeroExterior;
    @Column(name="numero_interior", length=50) private String numeroInterior;
    @Column(nullable=false, columnDefinition="text") private String colonia;
    @Column(nullable=false, length=250) private String municipio;
    @Column(nullable=false, length=250) private String estado;
    @Column(name="codigo_postal", nullable=false, length=5) private String codigoPostal;
    @Column(nullable=false, length=100) private String pais;
}
