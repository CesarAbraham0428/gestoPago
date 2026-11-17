package com.proyecto.servicios.service.Impl;

import com.proyecto.servicios.entity.sf.Cliente;
import com.proyecto.servicios.entity.sf.Cuenta;
import com.proyecto.servicios.models.request.ClienteConsultaRequest;
import jakarta.persistence.criteria.Predicate;
import com.proyecto.servicios.utils.Texto;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.data.jpa.domain.Specification;

/** Traduce los filtros opcionales de una consulta de clientes a una {@link Specification}. */
final class ClienteConsultaSpecification {

    private static final ZoneId ZONA = ZoneId.of("America/Mexico_City");

    private ClienteConsultaSpecification() {
    }

    /** @param propietarioId si no es nulo, limita el resultado a ese cliente (usuarios sin rol de administrador) */
    static Specification<Cliente> de(ClienteConsultaRequest request, Integer propietarioId) {
        return (cliente, consulta, builder) -> {
            List<Predicate> filtros = new ArrayList<>();
            if (propietarioId != null) {
                filtros.add(builder.equal(cliente.get("id"), propietarioId));
            }
            if (request.getRfc() != null) {
                filtros.add(builder.equal(builder.upper(cliente.get("rfc")), Texto.identidad(request.getRfc())));
            }
            if (request.getCurp() != null) {
                filtros.add(builder.equal(builder.upper(cliente.get("curp")), Texto.identidad(request.getCurp())));
            }
            if (request.getNombre() != null) {
                String nombre = "%" + request.getNombre().trim().toLowerCase(Locale.ROOT) + "%";
                filtros.add(builder.or(
                        builder.like(builder.lower(cliente.get("primerNombre")), nombre),
                        builder.like(builder.lower(cliente.get("segundoNombre")), nombre),
                        builder.like(builder.lower(cliente.get("apellidoPaterno")), nombre),
                        builder.like(builder.lower(cliente.get("apellidoMaterno")), nombre)));
            }
            if (request.getCorreo() != null) {
                filtros.add(builder.equal(builder.lower(cliente.get("correoElectronico")),
                        Texto.correo(request.getCorreo())));
            }
            if (request.getNumeroCuenta() != null) {
                var cuentasDelCliente = consulta.subquery(Integer.class);
                var cuenta = cuentasDelCliente.from(Cuenta.class);
                cuentasDelCliente.select(cuenta.get("cliente").get("id"))
                        .where(builder.equal(cuenta.get("numeroCuenta"), request.getNumeroCuenta()));
                filtros.add(cliente.get("id").in(cuentasDelCliente));
            }
            if (request.getActivo() != null) {
                filtros.add(builder.equal(cliente.get("activo"), request.getActivo()));
            }
            if (request.getDesde() != null) {
                filtros.add(builder.greaterThanOrEqualTo(cliente.get("fechaCreacion"),
                        request.getDesde().atStartOfDay(ZONA).toInstant()));
            }
            if (request.getHasta() != null) {
                filtros.add(builder.lessThan(cliente.get("fechaCreacion"),
                        request.getHasta().plusDays(1).atStartOfDay(ZONA).toInstant()));
            }
            return builder.and(filtros.toArray(Predicate[]::new));
        };
    }
}
