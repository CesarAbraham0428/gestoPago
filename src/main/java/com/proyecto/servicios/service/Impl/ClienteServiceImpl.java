package com.proyecto.servicios.service.Impl;
import com.proyecto.servicios.entity.sf.*;
import com.proyecto.servicios.model.*;
import com.proyecto.servicios.repositorys.sf.*;
import com.proyecto.servicios.service.ClienteService;
import com.proyecto.servicios.service.exception.NegocioException;
import com.proyecto.servicios.validation.PasswordValidator;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.Predicate;
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
    public ClienteServiceImpl(ClientesRepository clientes, DomicilioRepository domicilios,
        CuentaRepository cuentas, UsuarioRepository usuarios, PasswordEncoder encoder, EntityManager em) {
        this.clientes=clientes; this.domicilios=domicilios; this.cuentas=cuentas;
        this.usuarios=usuarios; this.encoder=encoder; this.em=em;
    }
    @Override @Transactional
    public ClienteResponse registrar(ClienteRequest r) {
        PasswordValidator.validar(r.password());
        validarEdad(r.fechaNacimiento());
        String curp=r.curp().trim().toUpperCase(Locale.ROOT);
        String rfc=r.rfc().trim().toUpperCase(Locale.ROOT);
        String correo=normalizarCorreo(r.correoElectronico());
        if (clientes.existsByCurp(curp)) conflicto("CURP duplicada");
        if (clientes.existsByRfc(rfc)) conflicto("RFC duplicado");
        if (clientes.existsByCorreoElectronicoIgnoreCase(correo)) conflicto("Correo electrónico duplicado");
        Cliente c=new Cliente();
        c.setPrimerNombre(r.primerNombre().trim()); c.setSegundoNombre(opcional(r.segundoNombre()));
        c.setApellidoPaterno(r.apellidoPaterno().trim()); c.setApellidoMaterno(r.apellidoMaterno().trim());
        c.setFechaNacimiento(r.fechaNacimiento()); c.setCurp(curp); c.setRfc(rfc);
        c.setSexo(r.sexo()); c.setNacionalidad(r.nacionalidad()); c.setEstadoCivil(r.estadoCivil());
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
        return respuesta(c);
    }
    @Override public ClienteResponse obtener(Integer id) { return respuesta(buscar(id)); }
    @Override
    public Page<ClienteResponse> consultar(String curp,String rfc,String correo,String numeroCuenta,
        Boolean activo,LocalDate desde,LocalDate hasta,int pagina,int tamanio) {
        if (pagina<0 || tamanio<1 || tamanio>100) invalido("Página inválida; tamaño permitido: 1 a 100");
        if (desde!=null && hasta!=null && desde.isAfter(hasta)) invalido("Rango de fechas inválido");
        Integer cuentaCliente = numeroCuenta==null ? null : cuentas.findByNumeroCuenta(numeroCuenta)
            .orElseThrow(() -> error(HttpStatus.NOT_FOUND,"Cuenta no encontrada")).getCliente().getId();
        return clientes.findAll((c,q,cb) -> {
            List<Predicate> filtros=new ArrayList<>();
            if(curp!=null) filtros.add(cb.equal(c.get("curp"),curp.trim().toUpperCase(Locale.ROOT)));
            if(rfc!=null) filtros.add(cb.equal(c.get("rfc"),rfc.trim().toUpperCase(Locale.ROOT)));
            if(correo!=null) filtros.add(cb.equal(c.get("correoElectronico"),normalizarCorreo(correo)));
            if(cuentaCliente!=null) filtros.add(cb.equal(c.get("id"),cuentaCliente));
            if(activo!=null) filtros.add(cb.equal(c.get("activo"),activo));
            if(desde!=null) filtros.add(cb.greaterThanOrEqualTo(c.get("fechaCreacion"),desde.atStartOfDay(ZONA).toInstant()));
            if(hasta!=null) filtros.add(cb.lessThan(c.get("fechaCreacion"),hasta.plusDays(1).atStartOfDay(ZONA).toInstant()));
            return cb.and(filtros.toArray(Predicate[]::new));
        },PageRequest.of(pagina,tamanio,Sort.by("id"))).map(this::respuesta);
    }
    @Override @Transactional
    public ClienteResponse actualizar(Integer id,ClienteActualizacionRequest r) {
        Cliente c=bloquear(id); validarEdad(r.fechaNacimiento());
        if(!c.isActivo()) conflicto("El cliente está inactivo");
        String correo=normalizarCorreo(r.correoElectronico());
        if(clientes.existsByCorreoElectronicoIgnoreCaseAndIdNot(correo,id)) conflicto("Correo electrónico duplicado");
        c.setPrimerNombre(r.primerNombre().trim()); c.setSegundoNombre(opcional(r.segundoNombre()));
        c.setApellidoPaterno(r.apellidoPaterno().trim()); c.setApellidoMaterno(r.apellidoMaterno().trim());
        c.setFechaNacimiento(r.fechaNacimiento()); c.setSexo(r.sexo()); c.setNacionalidad(r.nacionalidad());
        c.setEstadoCivil(r.estadoCivil()); c.setCorreoElectronico(correo); c.setTelefonoMovil(r.telefonoMovil());
        c.setTelefonoAlternativo(r.telefonoAlternativo()); c.setOcupacion(r.ocupacion().trim());
        c.setEmpresa(r.empresa().trim()); c.setIngresoMensual(r.ingresoMensual());
        Domicilio d=domicilios.findByClienteId(id).orElseThrow(() -> error(HttpStatus.NOT_FOUND,"Domicilio no encontrado"));
        aplicarDomicilio(d,r.domicilio());
        // El trigger sincroniza el correo del usuario. Vaciar el contexto evita datos obsoletos.
        em.flush(); em.clear(); return obtener(id);
    }
    @Override @Transactional
    public void desactivar(Integer id) {
        Cliente c=bloquear(id); c.setActivo(false);
        // Los triggers desactivan usuario y cuentas en esta misma transacción.
        em.flush(); em.clear();
    }
    private Cliente buscar(Integer id) {
        return clientes.findById(id).orElseThrow(() -> error(HttpStatus.NOT_FOUND,"Cliente no encontrado"));
    }
    private Cliente bloquear(Integer id) {
        return clientes.bloquearPorId(id).orElseThrow(() -> error(HttpStatus.NOT_FOUND,"Cliente no encontrado"));
    }
    private ClienteResponse respuesta(Cliente c) {
        Domicilio d=domicilios.findByClienteId(c.getId()).orElseThrow(() -> error(HttpStatus.NOT_FOUND,"Domicilio no encontrado"));
        Usuario u=usuarios.findByClienteId(c.getId()).orElseThrow(() -> error(HttpStatus.NOT_FOUND,"Usuario no encontrado"));
        return new ClienteResponse(c.getId(),c.getPrimerNombre(),c.getSegundoNombre(),c.getApellidoPaterno(),
            c.getApellidoMaterno(),c.getFechaNacimiento(),c.getCurp(),c.getRfc(),c.getSexo(),c.getNacionalidad(),
            c.getEstadoCivil(),c.getCorreoElectronico(),c.getTelefonoMovil(),c.getTelefonoAlternativo(),
            c.getOcupacion(),c.getEmpresa(),c.getIngresoMensual(),c.isActivo(),c.getFechaCreacion(),
            c.getFechaActualizacion(),ClienteMapper.domicilio(d),
            cuentas.findByClienteIdOrderByIdAsc(c.getId()).stream().map(ClienteMapper::cuenta).toList(),ClienteMapper.usuario(u));
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
    private void invalido(String mensaje) { throw error(HttpStatus.BAD_REQUEST,mensaje); }
    private NegocioException error(HttpStatus status,String mensaje) { return new NegocioException(status,mensaje); }
}
