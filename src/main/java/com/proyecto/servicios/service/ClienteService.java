package com.proyecto.servicios.service;
import com.proyecto.servicios.model.*;
import org.springframework.data.domain.Page;
import java.time.LocalDate;

public interface ClienteService {

    ClienteResponse registrar(ClienteRequest request);
    ClienteResponse obtener(Integer id);

    Page<?> consultar(Integer id, String curp, String rfc, String correo, String numeroCuenta,
        Boolean activo, LocalDate desde, LocalDate hasta, int pagina, int tamanio);

    ClienteResponse actualizarParcial(Integer id, com.fasterxml.jackson.databind.JsonNode cambios);
    void desactivar(Integer id);
}
