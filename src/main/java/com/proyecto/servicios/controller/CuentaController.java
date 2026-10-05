package com.proyecto.servicios.controller;
import com.proyecto.servicios.model.*;
import com.proyecto.servicios.service.CuentaService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;
import java.util.List;
@RestController @RequestMapping("/cuentas")
public class CuentaController {
    private final CuentaService service;
    public CuentaController(CuentaService service) { this.service=service; }
    @GetMapping public List<CuentaResponse> consultar(@RequestParam(required=false) Boolean activa) { return service.consultar(activa); }
    @GetMapping("/{numeroCuenta}") public CuentaResponse obtener(@PathVariable String numeroCuenta) { return service.obtener(numeroCuenta); }
    @GetMapping("/{numeroCuenta}/saldo") public BigDecimal saldo(@PathVariable String numeroCuenta) { return service.obtener(numeroCuenta).saldo(); }
    @PutMapping("/{numeroCuenta}/estado") public CuentaResponse estado(@PathVariable String numeroCuenta,@Valid @RequestBody EstadoCuentaRequest r) {
        return service.cambiarEstado(numeroCuenta,r.activa());
    }
    @DeleteMapping("/{numeroCuenta}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void desactivar(@PathVariable String numeroCuenta) { service.desactivar(numeroCuenta); }
}
