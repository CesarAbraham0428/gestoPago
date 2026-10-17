package com.proyecto.servicios.models.response;

import java.util.List;
import org.springframework.data.domain.Page;

public record ListaGenericResponse<T>(List<T> datos, int pagina, int tamanoPagina,
        long totalElementos, int totalPaginas) {

    public static <T> ListaGenericResponse<T> desde(Page<T> pagina) {
        return new ListaGenericResponse<>(pagina.getContent(), pagina.getNumber(),
                pagina.getSize(), pagina.getTotalElements(), pagina.getTotalPages());
    }
}
