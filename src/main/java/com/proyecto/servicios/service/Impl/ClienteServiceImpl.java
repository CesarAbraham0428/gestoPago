package com.proyecto.servicios.service.Impl;
import com.proyecto.servicios.entity.sf.*;
import com.proyecto.servicios.model.*;
import com.proyecto.servicios.repositorys.sf.*;
import com.proyecto.servicios.service.ClienteService;
import com.proyecto.servicios.service.exception.*;
import com.proyecto.servicios.validation.PasswordValidator;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;

@Service
@Transactional(readOnly=true)
public class ClienteServiceImpl implements ClienteService {

    private static final ZoneId ZONA = ZoneId.of("America/Mexico_City");
    private final ClientesRepository clientes;
    private final DomicilioRepository domicilios;
    private final CuentaRepository cuentas;
    private final UsuarioRepository usuarios;
    private final PasswordEncoder encoder;
    private final EntityManager em;
    private final com.fasterxml.jackson.databind.ObjectMapper mapper;
    private final jakarta.validation.Validator validator;

    public ClienteServiceImpl(ClientesRepository clientes, DomicilioRepository domicilios,
        CuentaRepository cuentas, UsuarioRepository usuarios, PasswordEncoder encoder, EntityManager em,
        com.fasterxml.jackson.databind.ObjectMapper mapper, jakarta.validation.Validator validator) {
        this.clientes=clientes; this.domicilios=domicilios; this.cuentas=cuentas;
        this.usuarios=usuarios; this.encoder=encoder; this.em=em;
        this.mapper=mapper; this.validator=validator;
    }
    @Override @Transactional
    public ClienteResponse registrar(ClienteRequest r) {

        PasswordValidator.validar(r.password());
        validarEdad(r.fechaNacimiento());
        String curp=r.curp().trim().toUpperCase(Locale.ROOT);
        String rfc=r.rfc().trim().toUpperCase(Locale.ROOT);
        String correo=normalizarCorreo(r.correoElectronico());
        if (clientes.existsByCurp(curp)) throw new CurpDuplicadaException();
        if (clientes.existsByRfc(rfc)) throw new RfcDuplicadoException();
        if (clientes.existsByCorreoElectronicoIgnoreCase(correo)) throw new CorreoDuplicadoException();
        Cliente c=new Cliente();
        c.setPrimerNombre(r.primerNombre().trim()); c.setSegundoNombre(opcional(r.segundoNombre()));
        c.setApellidoPaterno(r.apellidoPaterno().trim()); c.setApellidoMaterno(r.apellidoMaterno().trim());
        c.setFechaNacimiento(r.fechaNacimiento()); c.setCurp(curp); c.setRfc(rfc);
        c.setSexo(r.sexo()); c.setNacionalidad(r.nacionalidad().trim()); c.setEstadoCivil(r.estadoCivil());
        c.setCorreoElectronico(correo); c.setTelefonoMovil(r.telefonoMovil());
        c.setTelefonoAlternativo(r.telefonoAlternativo()); c.setOcupacion(r.ocupacion().trim());
        c.setEmpresa(r.empresa().trim()); c.setIngresoMensual(r.ingresoMensual());
        clientes.saveAndFlush(c);
        Domicilio d=new Domicilio(); d.setCliente(c); aplicarDomicilio(d,r.domicilio());
        domicilios.save(d);
        Cuenta cuenta=new Cuenta(); cuenta.setCliente(c);
        cuenta.setNumeroCuenta(cuentas.siguienteNumero()); cuenta.setSaldo(BigDecimal.ZERO);
        cuentas.save(cuenta);
        Usuario u=new Usuario(); u.setCliente(c); u.setCorreo(correo);
        u.setPasswordHash(encoder.encode(r.password())); usuarios.save(u);
        em.flush(); em.refresh(c); em.refresh(d); em.refresh(cuenta); em.refresh(u);
        return ClienteMapper.cliente(c, d, u, List.of(cuenta));
    }

    @Override public ClienteResponse obtener(Integer id) { return respuesta(buscar(id)); }
    @Override
    public Page<?> consultar(Integer id, String curp,String rfc,String correo,String numeroCuenta,
        Boolean activo,LocalDate desde,LocalDate hasta,int pagina,int tamanio) {
        if (pagina<0 || tamanio<1 || tamanio>100) invalido("Página inválida; tamaño permitido: 1 a 100");
        if (desde!=null && hasta!=null && desde.isAfter(hasta)) invalido("Rango de fechas inválido");
        Integer cuentaCliente = numeroCuenta==null ? null : cuentas.findClienteIdByNumeroCuenta(numeroCuenta.trim())
            .orElseThrow(CuentaNoEncontradaException::new);
        Specification<Cliente> filtrosConsulta = (c,q,cb) -> {
            List<Predicate> filtros=new ArrayList<>();
            if(id!=null) filtros.add(cb.equal(c.get("id"),id));
            if(curp!=null) filtros.add(cb.equal(c.get("curp"),curp.trim().toUpperCase(Locale.ROOT)));
            if(rfc!=null) filtros.add(cb.equal(c.get("rfc"),rfc.trim().toUpperCase(Locale.ROOT)));
            if(correo!=null) filtros.add(cb.equal(cb.lower(c.get("correoElectronico")),normalizarCorreo(correo)));
            if(cuentaCliente!=null) filtros.add(cb.equal(c.get("id"),cuentaCliente));
            if(activo!=null) filtros.add(cb.equal(c.get("activo"),activo));
            if(desde!=null) filtros.add(cb.greaterThanOrEqualTo(c.get("fechaCreacion"),desde.atStartOfDay(ZONA).toInstant()));
            if(hasta!=null) filtros.add(cb.lessThan(c.get("fechaCreacion"),hasta.plusDays(1).atStartOfDay(ZONA).toInstant()));
            return cb.and(filtros.toArray(Predicate[]::new));
        };
        if (id != null || curp != null || rfc != null || correo != null || numeroCuenta != null
            || activo != null || desde != null || hasta != null) {
            Page<Cliente> resultado = clientes.findAll(filtrosConsulta,
                PageRequest.of(pagina, tamanio, Sort.by("id")));
            if (resultado.getTotalElements() == 0) throw new ClienteNoEncontradoException();
            return respuestas(resultado);
        }
        return clientes.findBy(filtrosConsulta, query -> query.as(ClienteResumenProjection.class)
            .page(PageRequest.of(pagina, tamanio, Sort.by("id"))))
            .map(c -> new ClienteResumenResponse(c.getId(), c.getPrimerNombre(), c.getSegundoNombre(),
                c.getApellidoPaterno(), c.getApellidoMaterno(), c.isActivo(), c.getFechaCreacion()));
    }
    private ClienteResponse aplicarActualizacion(Cliente c, ClienteActualizacionRequest r) {
        Integer id = c.getId();
        validarEdad(r.fechaNacimiento());
        if(!c.isActivo()) conflicto("El cliente está inactivo");
        String correo=normalizarCorreo(r.correoElectronico());
        if(clientes.existsByCorreoElectronicoIgnoreCaseAndIdNot(correo,id)) throw new CorreoDuplicadoException();
        c.setPrimerNombre(r.primerNombre().trim()); c.setSegundoNombre(opcional(r.segundoNombre()));
        c.setApellidoPaterno(r.apellidoPaterno().trim()); c.setApellidoMaterno(r.apellidoMaterno().trim());
        c.setFechaNacimiento(r.fechaNacimiento()); c.setSexo(r.sexo()); c.setNacionalidad(r.nacionalidad().trim());
        c.setEstadoCivil(r.estadoCivil()); c.setCorreoElectronico(correo); c.setTelefonoMovil(r.telefonoMovil());
        c.setTelefonoAlternativo(r.telefonoAlternativo()); c.setOcupacion(r.ocupacion().trim());
        c.setEmpresa(r.empresa().trim()); c.setIngresoMensual(r.ingresoMensual());
        Domicilio d=domicilios.findByClienteId(id).orElseThrow(() -> error(HttpStatus.NOT_FOUND,"Domicilio no encontrado"));
        aplicarDomicilio(d,r.domicilio());
        // El trigger sincroniza el correo del usuario. Vaciar el contexto evita datos obsoletos.
        em.flush(); em.clear(); return obtener(id);
    }
    @Override @Transactional
    public ClienteResponse actualizarParcial(Integer id, com.fasterxml.jackson.databind.JsonNode cambios) {
        if (cambios == null || !cambios.isObject()) invalido("PATCH requiere un objeto JSON");
        Cliente c = bloquear(id);
        Domicilio d = domicilios.findByClienteId(id)
            .orElseThrow(() -> error(HttpStatus.NOT_FOUND, "Domicilio no encontrado"));
        ClienteActualizacionRequest actual = new ClienteActualizacionRequest(
            c.getPrimerNombre(), c.getSegundoNombre(), c.getApellidoPaterno(), c.getApellidoMaterno(),
            c.getFechaNacimiento(), c.getSexo(), c.getNacionalidad(), c.getEstadoCivil(),
            c.getCorreoElectronico(), c.getTelefonoMovil(), c.getTelefonoAlternativo(),
            c.getOcupacion(), c.getEmpresa(), c.getIngresoMensual(),
            new DomicilioRequest(d.getCalle(), d.getNumeroExterior(), d.getNumeroInterior(), d.getColonia(),
                d.getMunicipio(), d.getEstado(), d.getCodigoPostal(), d.getPais()));
        com.fasterxml.jackson.databind.node.ObjectNode combinado = mapper.valueToTree(actual);
        combinar(combinado, cambios, "");
        try {
            ClienteActualizacionRequest request = mapper.treeToValue(combinado, ClienteActualizacionRequest.class);
            var errores = validator.validate(request);
            if (!errores.isEmpty()) {
                var primero = errores.iterator().next();
                invalido(primero.getPropertyPath() + ": " + primero.getMessage());
            }
            return aplicarActualizacion(c, request);
        } catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
            throw new ValidacionException("Solicitud inválida: revisa los tipos y valores de los campos");
        }
    }

    private void combinar(com.fasterxml.jackson.databind.node.ObjectNode destino,
        com.fasterxml.jackson.databind.JsonNode cambios, String prefijo) {
        cambios.fields().forEachRemaining(campo -> {
            String nombre = campo.getKey();
            if (!destino.has(nombre)) invalido("Campo no permitido en actualización: " + prefijo + nombre);
            if (destino.get(nombre).isObject() && campo.getValue().isObject()) {
                combinar((com.fasterxml.jackson.databind.node.ObjectNode) destino.get(nombre),
                    campo.getValue(), prefijo + nombre + ".");
            } else {
                destino.set(nombre, campo.getValue());
            }
        });
    }

    @Override @Transactional
    public void desactivar(Integer id) {
        Cliente c=bloquear(id); c.setActivo(false);
        // Los triggers desactivan usuario y cuentas en esta misma transacción.
        em.flush(); em.clear();
    }
    private Cliente buscar(Integer id) {
        return clientes.findById(id).orElseThrow(() -> new ClienteNoEncontradoException());
    }
    private Cliente bloquear(Integer id) {
        return clientes.bloquearPorId(id).orElseThrow(() -> new ClienteNoEncontradoException());
    }
    private ClienteResponse respuesta(Cliente c) {
        Domicilio d=domicilios.findByClienteId(c.getId()).orElseThrow(() -> error(HttpStatus.NOT_FOUND,"Domicilio no encontrado"));
        Usuario u=usuarios.findByClienteId(c.getId()).orElseThrow(UsuarioNoEncontradoException::new);
        return ClienteMapper.cliente(c, d, u, cuentas.findByClienteIdOrderByIdAsc(c.getId()));
    }
    private Page<ClienteResponse> respuestas(Page<Cliente> pagina) {
        if (pagina.isEmpty()) return pagina.map(this::respuesta);
        List<Integer> ids = pagina.map(Cliente::getId).getContent();
        Map<Integer,Domicilio> direcciones = new HashMap<>();
        domicilios.findByClienteIdIn(ids).forEach(d -> direcciones.put(d.getCliente().getId(), d));
        Map<Integer,Usuario> accesos = new HashMap<>();
        usuarios.findByClienteIdIn(ids).forEach(u -> accesos.put(u.getCliente().getId(), u));
        Map<Integer,List<Cuenta>> cuentasPorCliente = new HashMap<>();
        cuentas.findByClienteIdInOrderByIdAsc(ids).forEach(c ->
            cuentasPorCliente.computeIfAbsent(c.getCliente().getId(), id -> new ArrayList<>()).add(c));
        return pagina.map(c -> {
            Domicilio d = direcciones.get(c.getId());
            Usuario u = accesos.get(c.getId());
            if (d == null) throw error(HttpStatus.NOT_FOUND, "Domicilio no encontrado");
            if (u == null) throw new UsuarioNoEncontradoException();
            return ClienteMapper.cliente(c, d, u, cuentasPorCliente.getOrDefault(c.getId(), List.of()));
        });
    }
    private void aplicarDomicilio(Domicilio d,DomicilioRequest r) {
        d.setCalle(r.calle().trim()); d.setNumeroExterior(r.numeroExterior().trim());
        d.setNumeroInterior(opcional(r.numeroInterior())); d.setColonia(r.colonia().trim());
        d.setMunicipio(r.municipio().trim()); d.setEstado(r.estado().trim());
        d.setCodigoPostal(r.codigoPostal()); d.setPais(r.pais().trim());
    }
    static String normalizarCorreo(String correo) { return correo.trim().toLowerCase(Locale.ROOT); }
    private String opcional(String value) { return value==null ? null : value.trim(); }
    private void validarEdad(LocalDate fecha) {
        LocalDate hoy=LocalDate.now(ZONA);
        if(fecha==null || fecha.isAfter(hoy.minusYears(18))) invalido("El cliente debe tener al menos 18 años y la fecha no puede ser futura");
    }
    private void conflicto(String mensaje) { throw error(HttpStatus.CONFLICT,mensaje); }
    private void invalido(String mensaje) { throw new ValidacionException(mensaje); }
    private NegocioException error(HttpStatus status,String mensaje) { return new NegocioException(status,mensaje); }
}
