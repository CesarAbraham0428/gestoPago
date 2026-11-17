package com.proyecto.servicios.entity.gestopago;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(name = "producto")
@Getter
@Setter
@NoArgsConstructor
public class Producto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "producto", nullable = false, columnDefinition = "TEXT")
    private String producto;

    @Column(name = "servicio", nullable = false, columnDefinition = "TEXT")
    private String servicio;

    @Column(name = "id_servicio", nullable = false)
    private Integer idServicio;

    @Column(name = "id_producto", nullable = false)
    private Integer idProducto;

    @Column(name = "id_cat_tipo_servicio")
    private Integer idCatTipoServicio;

    @Column(name = "tipo_front")
    private Integer tipoFront;

    @Column(name = "has_digito_verificador")
    private Boolean hasDigitoVerificador;

    @Column(name = "tipo_referencia", columnDefinition = "TEXT")
    private String tipoReferencia;

    @Column(name = "precio", precision = 12, scale = 2)
    private BigDecimal precio;

    @Column(name = "show_ayuda")
    private Boolean showAyuda;

    @Column(name = "legend", columnDefinition = "TEXT")
    private String legend;
}
