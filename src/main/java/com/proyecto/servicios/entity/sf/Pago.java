package com.proyecto.servicios.entity.sf;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
@Entity @Table(name="pagos") @Getter @Setter
public class Pago extends Auditado {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Integer id;
    @ManyToOne(fetch=FetchType.LAZY, optional=false)
    @JoinColumn(name="cuenta_id", nullable=false, updatable=false) private Cuenta cuenta;
    @Column(name="usuario_id", nullable=false, updatable=false) private Integer usuarioId;
    @Column(name="id_producto", nullable=false, updatable=false) private Integer idProducto;
    @Column(name="id_servicio", nullable=false, updatable=false) private Integer idServicio;
    @Column(name="tipo_front", updatable=false) private Integer tipoFront;
    @Column(length=20, updatable=false) private String telefono;
    @Column(length=100, updatable=false) private String referencia;
    @Column(nullable=false, precision=18, scale=2, updatable=false) private BigDecimal monto;
    @Column(nullable=false, unique=true, length=15, updatable=false) private String upc;
    @Enumerated(EnumType.STRING) @Column(nullable=false, length=20) private EstadoPago estado;
    @Column(name="codigo_respuesta", length=4) private String codigoRespuesta;
    @Column(name="id_tx") private Long idTx;
    @Column(name="num_autorizacion", length=60) private String numAutorizacion;
    @Column(precision=18, scale=2) private BigDecimal comision;
    @Column(length=250) private String mensaje;
    @Column(columnDefinition="TEXT", updatable=false) private String peticion;
    @Column(columnDefinition="TEXT") private String respuesta;
    @Column(name="respuesta_confirmacion", columnDefinition="TEXT") private String respuestaConfirmacion;
}
