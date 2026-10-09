package com.proyecto.servicios.service;
import com.proyecto.servicios.entity.sf.*;
import com.proyecto.servicios.model.*;
import com.proyecto.servicios.repositorys.sf.*;
import com.proyecto.servicios.service.exception.*;
import jakarta.persistence.EntityManager;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.*;

@Service
@Transactional(readOnly=true)

public class CuentaService {

    private final CuentaRepository cuentas;
    private final ClientesRepository clientes;
    private final EntityManager em;

    public CuentaService(CuentaRepository cuentas,ClientesRepository clientes,EntityManager em) {
        this.cuentas=cuentas; this.clientes=clientes; this.em=em;
    }

    public BigDecimal saldo(String numero) {
        return cuentas.findSaldoByNumeroCuenta(numero).orElseThrow(CuentaNoEncontradaException::new);
    }

    public Page<?> consultar(Boolean activa, int pagina, int tamanio) {
        return consultar(activa, null, null, pagina, tamanio);
    }

    public Page<?> consultar(Boolean activa, BigDecimal saldo, Integer id, int pagina, int tamanio) {
        if (pagina < 0 || tamanio < 1 || tamanio > 100) {
            throw new ValidacionException("Página inválida; tamaño permitido: 1 a 100");
        }
        if (saldo != null && saldo.signum() < 0) throw new ValidacionException("El saldo no puede ser negativo");
        Pageable pageable = PageRequest.of(pagina, tamanio, Sort.by("id"));
        if (activa == null && saldo == null && id == null) return cuentas.consultarResumen(null, pageable);
        Page<Cuenta> resultado = cuentas.findAll((c, q, cb) -> {
            List<Predicate> filtros = new ArrayList<>();
            if (activa != null) filtros.add(cb.equal(c.get("estaActiva"), activa));
            if (saldo != null) filtros.add(cb.equal(c.get("saldo"), saldo));
            if (id != null) filtros.add(cb.equal(c.get("id"), id));
            return cb.and(filtros.toArray(Predicate[]::new));
        }, pageable);
        if (resultado.getTotalElements() == 0) throw new CuentaNoEncontradaException();
        return resultado.map(ClienteMapper::cuenta);
    }

    public CuentaResponse obtenerPorId(Integer id) {
        return ClienteMapper.cuenta(cuentas.findById(id).orElseThrow(CuentaNoEncontradaException::new));
    }

    public CuentaResponse obtener(String numero) { return ClienteMapper.cuenta(buscar(numero)); }

    @Transactional
    public CuentaResponse cambiarEstado(String numero,boolean activa) {
        Cuenta cuenta=buscar(numero);
        Cliente cliente=clientes.bloquearPorId(cuenta.getCliente().getId())
            .orElseThrow(() -> new ClienteNoEncontradoException());
        em.refresh(cuenta);
        if(activa && !cliente.isActivo()) throw new NegocioException(HttpStatus.CONFLICT,"No puedes activar una cuenta de un cliente inactivo");
        cuenta.setEstaActiva(activa); em.flush(); em.refresh(cuenta);
        return ClienteMapper.cuenta(cuenta);
    }

    @Transactional public void desactivar(String numero) { cambiarEstado(numero,false); }
    private Cuenta buscar(String numero) {
        return cuentas.findByNumeroCuenta(numero).orElseThrow(() -> new CuentaNoEncontradaException());
    }
}
