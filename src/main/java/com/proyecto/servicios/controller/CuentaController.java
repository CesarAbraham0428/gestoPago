package com.proyecto.servicios.controller;
import com.proyecto.servicios.model.*;
import com.proyecto.servicios.service.CuentaService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;
import org.springframework.data.domain.Page;

@RestController
@RequestMapping("/cuentas")

public class CuentaController {

    private final CuentaService service;

    public CuentaController(CuentaService service) { this.service=service; }

    @GetMapping
    public Page<?> consultar(@RequestParam(required=false) Boolean activa,
        @RequestParam(required=false) Boolean activo, @RequestParam(required=false) BigDecimal saldo,
        @RequestParam(required=false) Integer id,
        @RequestParam(defaultValue="0") int pagina, @RequestParam(defaultValue="20") int tamanio,
        @RequestParam java.util.Map<String,String> parametros) {
        java.util.Set<String> permitidos = java.util.Set.of("activa", "activo", "saldo", "id", "pagina", "tamanio");
        if (!permitidos.containsAll(parametros.keySet())) {
            throw new com.proyecto.servicios.service.exception.ValidacionException("Filtro de cuenta no permitido; usa id, activo o saldo");
        }
        if (activo != null && activa != null && !activo.equals(activa)) {
            throw new com.proyecto.servicios.service.exception.ValidacionException("activo y activa no pueden tener valores distintos");
        }
        return service.consultar(activo != null ? activo : activa, saldo, id, pagina, tamanio);
    }
    @GetMapping("/id/{id}") public CuentaResponse obtenerPorId(@PathVariable Integer id) { return service.obtenerPorId(id); }
    @GetMapping("/{numeroCuenta}") public CuentaResponse obtener(@PathVariable String numeroCuenta) { return service.obtener(numeroCuenta); }
    @GetMapping("/{numeroCuenta}/saldo") public BigDecimal saldo(@PathVariable String numeroCuenta) { return service.saldo(numeroCuenta); }
    @PutMapping("/{numeroCuenta}/estado") public CuentaResponse estado(@PathVariable String numeroCuenta,@Valid @RequestBody EstadoCuentaRequest r) {
        return service.cambiarEstado(numeroCuenta,r.activa());
    }

    @DeleteMapping("/{numeroCuenta}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void desactivar(@PathVariable String numeroCuenta) { service.desactivar(numeroCuenta); }
}
