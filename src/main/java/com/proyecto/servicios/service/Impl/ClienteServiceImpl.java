package com.proyecto.servicios.service.Impl;
import com.proyecto.servicios.entity.sf.Cliente;
import com.proyecto.servicios.entity.sf.Cuenta;
import com.proyecto.servicios.entity.sf.Domicilio;
import com.proyecto.servicios.entity.sf.Usuario;
import com.proyecto.servicios.models.request.ClienteActualizacionPorIdentidadRequest;
import com.proyecto.servicios.models.request.ClienteActualizacionRequest;
import com.proyecto.servicios.models.request.ClienteConsultaRequest;
import com.proyecto.servicios.models.request.ClienteIdentificacionRequest;
import com.proyecto.servicios.models.request.ClienteRequest;
import com.proyecto.servicios.models.request.DomicilioRequest;
import com.proyecto.servicios.models.response.ClienteResponse;
import com.proyecto.servicios.models.response.ListaGenericResponse;
import com.proyecto.servicios.mapper.ClienteMapper;
import com.proyecto.servicios.repositorys.sf.ClientesRepository;
import com.proyecto.servicios.repositorys.sf.CuentaRepository;
import com.proyecto.servicios.repositorys.sf.DomicilioRepository;
import com.proyecto.servicios.repositorys.sf.UsuarioRepository;
import com.proyecto.servicios.service.ClienteService;
import com.proyecto.servicios.service.AutorizacionService;
import com.proyecto.servicios.service.exception.ClienteNoEncontradoException;
import com.proyecto.servicios.service.exception.CorreoDuplicadoException;
import com.proyecto.servicios.service.exception.CurpDuplicadaException;
import com.proyecto.servicios.service.exception.NegocioException;
import com.proyecto.servicios.service.exception.RfcDuplicadoException;
import com.proyecto.servicios.service.exception.UsuarioNoEncontradoException;
import com.proyecto.servicios.service.exception.ValidacionException;
import com.proyecto.servicios.validation.PasswordValidator;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

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
    private final AutorizacionService autorizacion;

    public ClienteServiceImpl(ClientesRepository clientes, DomicilioRepository domicilios,
        CuentaRepository cuentas, UsuarioRepository usuarios, PasswordEncoder encoder, EntityManager em,
        com.fasterxml.jackson.databind.ObjectMapper mapper, jakarta.validation.Validator validator,
        AutorizacionService autorizacion) {
        this.clientes=clientes; this.domicilios=domicilios; this.cuentas=cuentas;
        this.usuarios=usuarios; this.encoder=encoder; this.em=em;
        this.mapper=mapper; this.validator=validator;
        this.autorizacion = autorizacion;
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

    @Override
    public ListaGenericResponse<ClienteResponse> consultar(ClienteConsultaRequest request,
            Integer usuarioAutenticadoId) {
        validarEntrada(request);
        Integer propietarioId = autorizacion.clienteIdParaConsulta(usuarioAutenticadoId);
        Specification<Cliente> filtrosConsulta = (cliente, consulta, builder) -> {
            List<Predicate> filtros = new ArrayList<>();
            if (propietarioId != null) filtros.add(builder.equal(cliente.get("id"), propietarioId));
            if (request.getRfc() != null) {
                filtros.add(builder.equal(builder.upper(cliente.get("rfc")), normalizarIdentidad(request.getRfc())));
            }
            if (request.getCurp() != null) {
                filtros.add(builder.equal(builder.upper(cliente.get("curp")), normalizarIdentidad(request.getCurp())));
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
                        normalizarCorreo(request.getCorreo())));
            }
            if (request.getNumeroCuenta() != null) {
                var cuentasDelCliente = consulta.subquery(Integer.class);
                var cuenta = cuentasDelCliente.from(Cuenta.class);
                cuentasDelCliente.select(cuenta.get("cliente").get("id"))
                        .where(builder.equal(cuenta.get("numeroCuenta"), request.getNumeroCuenta()));
                filtros.add(cliente.get("id").in(cuentasDelCliente));
            }
            if (request.getActivo() != null) filtros.add(builder.equal(cliente.get("activo"), request.getActivo()));
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
        Pageable pageable = PageRequest.of(request.getPagina(), request.getTamanio(), Sort.by("id"));
        Page<Cliente> pagina = clientes.findAll(filtrosConsulta, pageable);
        if (pagina.isEmpty()) {
            return ListaGenericResponse.desde(pagina.map(this::respuesta));
        }
        // Cargar las relaciones de toda la página evita consultas adicionales por cada cliente.
        List<Integer> ids = pagina.stream().map(Cliente::getId).toList();
        var domiciliosPorCliente = domicilios.findByClienteIdIn(ids).stream()
                .collect(Collectors.toMap(d -> d.getCliente().getId(), Function.identity()));
        var usuariosPorCliente = usuarios.findByClienteIdIn(ids).stream()
                .collect(Collectors.toMap(u -> u.getCliente().getId(), Function.identity()));
        var cuentasPorCliente = cuentas.findByClienteIdInOrderByIdAsc(ids).stream()
                .collect(Collectors.groupingBy(c -> c.getCliente().getId()));
        Page<ClienteResponse> resultado = pagina.map(cliente -> {
            Domicilio domicilio = domiciliosPorCliente.get(cliente.getId());
            if (domicilio == null) throw error(HttpStatus.NOT_FOUND, "Domicilio no encontrado");
            Usuario usuario = usuariosPorCliente.get(cliente.getId());
            if (usuario == null) throw new UsuarioNoEncontradoException();
            return ClienteMapper.cliente(cliente, domicilio, usuario,
                    cuentasPorCliente.getOrDefault(cliente.getId(), List.of()));
        });
        return ListaGenericResponse.desde(resultado);
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
        em.flush(); em.clear(); return respuesta(buscar(id));
    }
    @Override @Transactional
    public ClienteResponse actualizarParcial(ClienteActualizacionPorIdentidadRequest request,
            Integer usuarioAutenticadoId) {
        validarEntrada(request);
        com.fasterxml.jackson.databind.JsonNode cambios = request.cambios();
        if (cambios == null || !cambios.isObject()) invalido("PATCH requiere un objeto JSON");
        Cliente identificado = buscar(request.identificacion());
        autorizacion.autorizarCliente(identificado.getId(), usuarioAutenticadoId);
        Integer id = identificado.getId();
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
            ClienteActualizacionRequest actualizacion = mapper.treeToValue(combinado, ClienteActualizacionRequest.class);
            validarEntrada(actualizacion);
            return aplicarActualizacion(c, actualizacion);
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
    public void desactivar(ClienteIdentificacionRequest request, Integer usuarioAutenticadoId) {
        validarEntrada(request);
        Cliente identificado = buscar(request);
        autorizacion.autorizarCliente(identificado.getId(), usuarioAutenticadoId);
        Cliente c = bloquear(identificado.getId());
        c.setActivo(false);
        // Los triggers desactivan usuario y cuentas en esta misma transacción.
        em.flush(); em.clear();
    }
    private Cliente buscar(Integer id) {
        return clientes.findById(id).orElseThrow(() -> new ClienteNoEncontradoException());
    }
    private Cliente bloquear(Integer id) {
        Cliente cliente = clientes.bloquearPorId(id).orElseThrow(ClienteNoEncontradoException::new);
        // La búsqueda por identidad puede haber cargado la entidad antes de esperar el bloqueo.
        em.refresh(cliente);
        return cliente;
    }
    private Cliente buscar(ClienteIdentificacionRequest request) {
        Optional<Cliente> encontrado;
        if (request.rfc() != null && request.curp() != null) {
            encontrado = clientes.findByRfcIgnoreCaseAndCurpIgnoreCase(
                    normalizarIdentidad(request.rfc()), normalizarIdentidad(request.curp()));
        } else if (request.rfc() != null) {
            encontrado = clientes.findByRfcIgnoreCase(normalizarIdentidad(request.rfc()));
        } else {
            encontrado = clientes.findByCurpIgnoreCase(normalizarIdentidad(request.curp()));
        }
        return encontrado.orElseThrow(ClienteNoEncontradoException::new);
    }

    private void validarEntrada(Object request) {
        if (request == null) throw new ValidacionException("La solicitud es obligatoria");
        var errores = validator.validate(request);
        if (!errores.isEmpty()) throw new jakarta.validation.ConstraintViolationException(errores);
    }

    private static String normalizarIdentidad(String identidad) {
        return identidad.trim().toUpperCase(Locale.ROOT);
    }
    private ClienteResponse respuesta(Cliente c) {
        Domicilio d=domicilios.findByClienteId(c.getId()).orElseThrow(() -> error(HttpStatus.NOT_FOUND,"Domicilio no encontrado"));
        Usuario u=usuarios.findByClienteId(c.getId()).orElseThrow(UsuarioNoEncontradoException::new);
        return ClienteMapper.cliente(c, d, u, cuentas.findByClienteIdOrderByIdAsc(c.getId()));
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
