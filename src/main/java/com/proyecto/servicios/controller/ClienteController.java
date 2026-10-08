package com.proyecto.servicios.controller;

import com.proyecto.servicios.model.*;
import com.proyecto.servicios.service.ClienteService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;

@RestController 
@RequestMapping("/clientes")

public class ClienteController {

    private final ClienteService service;

    public ClienteController(ClienteService service) { this.service=service; }
    
    @PostMapping public ResponseEntity<ClienteResponse> registrar(@Valid @RequestBody ClienteRequest request) {
        ClienteResponse c=service.registrar(request);
        return ResponseEntity.created(java.net.URI.create("/clientes/"+c.id())).body(c);
    }

    @GetMapping public Page<ClienteResponse> consultar(
        @RequestParam(required=false) String curp, @RequestParam(required=false) String rfc,
        @RequestParam(required=false) String correo, @RequestParam(required=false) String numeroCuenta,
        @RequestParam(required=false) Boolean activo,
        @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate desde,
        @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate hasta,
        @RequestParam(defaultValue="0") int pagina,@RequestParam(defaultValue="20") int tamanio) {
        return service.consultar(curp,rfc,correo,numeroCuenta,activo,desde,hasta,pagina,tamanio);
    }

    @GetMapping("/{id}") public ClienteResponse obtener(@PathVariable Integer id) { return service.obtener(id); }
    @PutMapping("/{id}") public ClienteResponse actualizar(@PathVariable Integer id,@Valid @RequestBody ClienteActualizacionRequest r) {
        return service.actualizar(id,r);
    }

    @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void desactivar(@PathVariable Integer id) { service.desactivar(id); }
}
