package com.proyecto.servicios.repositorys.sf;

import java.time.Instant;

public interface ClienteResumenProjection {
    Integer getId();
    String getPrimerNombre();
    String getSegundoNombre();
    String getApellidoPaterno();
    String getApellidoMaterno();
    boolean isActivo();
    Instant getFechaCreacion();
}
