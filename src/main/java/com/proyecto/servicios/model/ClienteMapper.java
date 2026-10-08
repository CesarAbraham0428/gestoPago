package com.proyecto.servicios.model;
import com.proyecto.servicios.entity.sf.*;

public final class ClienteMapper {
    
    private ClienteMapper() {}
    public static CuentaResponse cuenta(Cuenta c) {
        return new CuentaResponse(c.getId(),c.getCliente().getId(),c.getNumeroCuenta(),c.getSaldo(),
            c.isEstaActiva(),c.getFechaCreacion(),c.getFechaActualizacion());
    }
    public static UsuarioResponse usuario(Usuario u) {
        return new UsuarioResponse(u.getId(),u.getCliente().getId(),u.getCorreo(),u.isActivo(),
            u.getFechaCreacion(),u.getFechaActualizacion());
    }
    public static DomicilioResponse domicilio(Domicilio d) {
        return new DomicilioResponse(d.getId(),d.getCalle(),d.getNumeroExterior(),d.getNumeroInterior(),
            d.getColonia(),d.getMunicipio(),d.getEstado(),d.getCodigoPostal(),d.getPais());
    }
}
