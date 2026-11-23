package com.proyecto.servicios.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.proyecto.servicios.models.response.GenericResponse;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

/** Los filtros de seguridad se ejecutan antes del manejo de excepciones de MVC. */
@Component
public class ApiSecurityErrorWriter {
    private final ObjectMapper mapper;

    public ApiSecurityErrorWriter(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public void escribir(HttpServletResponse response, HttpStatus status, String mensaje) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        mapper.writeValue(response.getWriter(), GenericResponse.error(mensaje));
    }
}
