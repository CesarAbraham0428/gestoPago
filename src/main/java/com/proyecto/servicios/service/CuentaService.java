package com.proyecto.servicios.service;

import com.proyecto.servicios.entity.sf.Cliente;
import com.proyecto.servicios.entity.sf.Cuenta;
import com.proyecto.servicios.mapper.ClienteMapper;
import com.proyecto.servicios.models.request.CuentaConsultaRequest;
import com.proyecto.servicios.models.response.CuentaResponse;
import com.proyecto.servicios.models.response.ListaGenericResponse;
import com.proyecto.servicios.repositorys.sf.ClientesRepository;
import com.proyecto.servicios.repositorys.sf.CuentaRepository;
import com.proyecto.servicios.service.exception.ClienteNoEncontradoException;
import com.proyecto.servicios.service.exception.CuentaNoEncontradaException;
import com.proyecto.servicios.service.exception.NegocioException;
import com.proyecto.servicios.service.exception.ValidacionException;
import jakarta.persistence.EntityManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class CuentaService {

    private final CuentaRepository cuentas;
    private final ClientesRepository clientes;
    private final EntityManager em;
    private final AutorizacionService autorizacion;
    private final PasswordEncoder passwordEncoder;

    public CuentaService(CuentaRepository cuentas, ClientesRepository clientes, EntityManager em,
            AutorizacionService autorizacion, PasswordEncoder passwordEncoder) {
        this.cuentas = cuentas;
        this.clientes = clientes;
        this.em = em;
        this.autorizacion = autorizacion;
        this.passwordEncoder = passwordEncoder;
    }

    public ListaGenericResponse<CuentaResponse> consultar(
            CuentaConsultaRequest request, Integer usuarioAutenticadoId) {
        if (request.pagina() < 0 || request.tamanio() < 1 || request.tamanio() > 100) {
            throw new ValidacionException("Página inválida; tamaño permitido: 1 a 100");
        }
        if (request.saldo() != null && request.saldo().signum() < 0) {
            throw new ValidacionException("El saldo no puede ser negativo");
        }
        Integer clienteId = autorizacion.clienteIdParaConsulta(usuarioAutenticadoId);
        Pageable pageable = PageRequest.of(request.pagina(), request.tamanio(), Sort.by("id"));
        Page<CuentaResponse> resultado = cuentas.consultar(request.activo(), request.saldo(),
                request.numeroCuenta(), clienteId, pageable);
        return ListaGenericResponse.desde(resultado);
    }

    @Transactional
    public CuentaResponse cambiarEstado(String numeroCuenta, boolean activa, Integer usuarioAutenticadoId) {
        Cuenta cuenta = buscarAutorizada(numeroCuenta, usuarioAutenticadoId);
        Cliente cliente = clientes.bloquearPorId(cuenta.getCliente().getId())
                .orElseThrow(ClienteNoEncontradoException::new);
        em.refresh(cliente);
        em.refresh(cuenta);
        if (activa && !cliente.isActivo()) {
            throw new NegocioException(HttpStatus.CONFLICT,
                    "No puedes activar una cuenta de un cliente inactivo");
        }
        cuenta.setEstaActiva(activa);
        em.flush();
        em.refresh(cuenta);
        return ClienteMapper.cuenta(cuenta);
    }

    /** Registra o cambia el PIN de autorización de pagos; para cambiarlo se exige el PIN actual. */
    @Transactional
    public void configurarPin(String numeroCuenta, String pinActual, String pinNuevo, Integer usuarioAutenticadoId) {
        Cuenta cuenta = buscarAutorizada(numeroCuenta, usuarioAutenticadoId);
        if (cuenta.getPinHash() != null
                && (pinActual == null || !passwordEncoder.matches(pinActual, cuenta.getPinHash()))) {
            throw new NegocioException(HttpStatus.FORBIDDEN, "El PIN actual es incorrecto");
        }
        cuenta.setPinHash(passwordEncoder.encode(pinNuevo));
    }

    private Cuenta buscarAutorizada(String numeroCuenta, Integer usuarioAutenticadoId) {
        Integer clienteId = autorizacion.clienteIdParaConsulta(usuarioAutenticadoId);
        return (clienteId == null
                ? cuentas.findByNumeroCuenta(numeroCuenta)
                : cuentas.findByNumeroCuentaAndClienteId(numeroCuenta, clienteId))
                .orElseThrow(CuentaNoEncontradaException::new);
    }
}
