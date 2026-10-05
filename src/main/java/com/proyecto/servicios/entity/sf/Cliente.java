package com.proyecto.servicios.entity.sf;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.math.BigDecimal;
import java.time.LocalDate;
@Entity @Table(name="clientes") @Getter @Setter
public class Cliente extends Auditado {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Integer id;
    @Column(name="primer_nombre", nullable=false, length=50) private String primerNombre;
    @Column(name="segundo_nombre", length=50) private String segundoNombre;
    @Column(name="apellido_paterno", nullable=false, length=50) private String apellidoPaterno;
    @Column(name="apellido_materno", nullable=false, length=50) private String apellidoMaterno;
    @Column(name="fecha_nacimiento", nullable=false) private LocalDate fechaNacimiento;
    @Column(nullable=false, unique=true, length=18, updatable=false) private String curp;
    @Column(nullable=false, unique=true, length=13, updatable=false) private String rfc;
    @Enumerated(EnumType.STRING) @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(nullable=false, columnDefinition="sexo_enum") private Sexo sexo;
    @Enumerated(EnumType.STRING) @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(nullable=false, columnDefinition="nacionalidad_enum") private Nacionalidad nacionalidad = Nacionalidad.Mexicana;
    @Convert(converter=EstadoCivilConverter.class) @org.hibernate.annotations.ColumnTransformer(write="?::estado_civil_enum")
    @Column(name="estado_civil", nullable=false, columnDefinition="estado_civil_enum") private EstadoCivil estadoCivil;
    @Column(name="correo_electronico", nullable=false, length=100) private String correoElectronico;
    @Column(name="telefono_movil", nullable=false, length=10) private String telefonoMovil;
    @Column(name="telefono_alternativo", length=10) private String telefonoAlternativo;
    @Column(nullable=false, length=250) private String ocupacion;
    @Column(nullable=false, length=250) private String empresa;
    @Column(name="ingreso_mensual", nullable=false, precision=18, scale=2) private BigDecimal ingresoMensual;
    @Enumerated(EnumType.STRING) @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(nullable=false, columnDefinition="rol_enum") private Rol rol = Rol.Cliente;
    @Column(nullable=false) private boolean activo = true;
}
