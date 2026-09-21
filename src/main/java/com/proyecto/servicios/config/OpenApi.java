package com.proyecto.servicios.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;

public class OpenApi {
    @Bean
    public OpenAPI openAPI(){
        return new OpenAPI().addServersItem(new Server().url("http://localhost:8081"));
    }
}
