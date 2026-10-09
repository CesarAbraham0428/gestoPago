package com.proyecto.servicios.entity.sf;
import jakarta.persistence.*;
import lombok.Getter;
import java.time.Instant;
@MappedSuperclass
@Getter
public abstract class Auditado {
    @Column(name="fecha_creacion", insertable=false, updatable=false)
    protected Instant fechaCreacion;
    @Column(name="fecha_actualizacion", insertable=false, updatable=false)
    protected Instant fechaActualizacion;
}
