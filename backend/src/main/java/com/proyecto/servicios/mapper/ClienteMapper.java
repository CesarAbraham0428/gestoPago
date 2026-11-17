package com.proyecto.servicios.mapper;

import com.proyecto.servicios.entity.sf.Cliente;
import com.proyecto.servicios.entity.sf.Cuenta;
import com.proyecto.servicios.entity.sf.Domicilio;
import com.proyecto.servicios.entity.sf.Usuario;
import com.proyecto.servicios.models.response.ClienteResponse;
import com.proyecto.servicios.models.response.CuentaResponse;
import com.proyecto.servicios.models.response.DomicilioResponse;
import com.proyecto.servicios.models.response.UsuarioResponse;

import java.util.List;

public final class ClienteMapper {

    private ClienteMapper() {
    }

    public static ClienteResponse cliente(Cliente cliente, Domicilio domicilio, Usuario usuario,
                                          List<Cuenta> cuentasCliente) {
        return new ClienteResponse(
            cliente.getId(), cliente.getPrimerNombre(), cliente.getSegundoNombre(),
            cliente.getApellidoPaterno(), cliente.getApellidoMaterno(), cliente.getFechaNacimiento(),
            cliente.getCurp(), cliente.getRfc(), cliente.getSexo(), cliente.getNacionalidad(),
            cliente.getEstadoCivil(), cliente.getCorreoElectronico(), cliente.getTelefonoMovil(),
            cliente.getTelefonoAlternativo(), cliente.getOcupacion(), cliente.getEmpresa(),
            cliente.getIngresoMensual(), cliente.getRol(), cliente.isActivo(), cliente.getFechaCreacion(),
            cliente.getFechaActualizacion(), domicilio(domicilio),
            cuentasCliente.stream().map(ClienteMapper::cuenta).toList(), usuario(usuario)
        );
    }

    public static CuentaResponse cuenta(Cuenta cuenta) {
        return new CuentaResponse(
            cuenta.getId(), cuenta.getCliente().getId(), cuenta.getNumeroCuenta(), cuenta.getSaldo(),
            cuenta.isEstaActiva(), cuenta.getFechaCreacion(), cuenta.getFechaActualizacion()
        );
    }

    public static UsuarioResponse usuario(Usuario usuario) {
        return new UsuarioResponse(
            usuario.getId(), usuario.getCliente().getId(), usuario.getCorreo(), usuario.isActivo(),
            usuario.getFechaCreacion(), usuario.getFechaActualizacion()
        );
    }

    public static DomicilioResponse domicilio(Domicilio domicilio) {
        return new DomicilioResponse(
            domicilio.getId(), domicilio.getCalle(), domicilio.getNumeroExterior(),
            domicilio.getNumeroInterior(), domicilio.getColonia(), domicilio.getMunicipio(),
            domicilio.getEstado(), domicilio.getCodigoPostal(), domicilio.getPais()
        );
    }
}
