package com.proyecto.servicios.service;
import com.proyecto.servicios.model.*;
import org.springframework.data.domain.Page;
import java.time.LocalDate;

public interface ClienteService {

    ClienteResponse registrar(ClienteRequest request);
    ClienteResponse obtener(Integer id);

    Page<ClienteResponse> consultar(String curp, String rfc, String correo, String numeroCuenta,
        Boolean activo, LocalDate desde, LocalDate hasta, int pagina, int tamanio);
        
    ClienteResponse actualizar(Integer id, ClienteActualizacionRequest request);
    void desactivar(Integer id);
}
