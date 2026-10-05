package com.proyecto.servicios.service;
import com.proyecto.servicios.entity.sf.*;
import com.proyecto.servicios.model.*;
import com.proyecto.servicios.repositorys.sf.*;
import com.proyecto.servicios.service.exception.NegocioException;
import jakarta.persistence.EntityManager;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
@Service @Transactional(readOnly=true)
public class CuentaService {
    private final CuentaRepository cuentas;
    private final ClientesRepository clientes;
    private final EntityManager em;
    public CuentaService(CuentaRepository cuentas,ClientesRepository clientes,EntityManager em) {
        this.cuentas=cuentas; this.clientes=clientes; this.em=em;
    }
    public CuentaResponse obtener(String numero) { return ClienteMapper.cuenta(buscar(numero)); }
    public List<CuentaResponse> consultar(Boolean activas) {
        return (activas==null ? cuentas.findAll() : cuentas.findByEstaActiva(activas))
            .stream().map(ClienteMapper::cuenta).toList();
    }
    @Transactional
    public CuentaResponse cambiarEstado(String numero,boolean activa) {
        Cuenta cuenta=buscar(numero);
        Cliente cliente=clientes.bloquearPorId(cuenta.getCliente().getId())
            .orElseThrow(() -> new NegocioException(HttpStatus.NOT_FOUND,"Cliente no encontrado"));
        em.refresh(cuenta);
        if(activa && !cliente.isActivo()) throw new NegocioException(HttpStatus.CONFLICT,"No puedes activar una cuenta de un cliente inactivo");
        cuenta.setEstaActiva(activa); em.flush(); em.refresh(cuenta);
        return ClienteMapper.cuenta(cuenta);
    }
    @Transactional public void desactivar(String numero) { cambiarEstado(numero,false); }
    private Cuenta buscar(String numero) {
        return cuentas.findByNumeroCuenta(numero).orElseThrow(() -> new NegocioException(HttpStatus.NOT_FOUND,"Cuenta no encontrada"));
    }
}
