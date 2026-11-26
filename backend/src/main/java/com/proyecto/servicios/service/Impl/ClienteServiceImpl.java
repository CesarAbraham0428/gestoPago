package com.proyecto.servicios.service.Impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.proyecto.servicios.entity.sf.Cliente;
import com.proyecto.servicios.entity.sf.Cuenta;
import com.proyecto.servicios.entity.sf.Domicilio;
import com.proyecto.servicios.entity.sf.Usuario;
import com.proyecto.servicios.mapper.ClienteMapper;
import com.proyecto.servicios.models.request.ClienteActualizacionPorIdentidadRequest;
import com.proyecto.servicios.models.request.ClienteActualizacionRequest;
import com.proyecto.servicios.models.request.ClienteConsultaRequest;
import com.proyecto.servicios.models.request.ClienteDisponibilidadRequest;
import com.proyecto.servicios.models.request.ClienteIdentificacionRequest;
import com.proyecto.servicios.models.request.ClienteRequest;
import com.proyecto.servicios.models.request.DatosCliente;
import com.proyecto.servicios.models.request.DomicilioRequest;
import com.proyecto.servicios.models.response.ClienteResponse;
import com.proyecto.servicios.models.response.ListaGenericResponse;
import com.proyecto.servicios.repositorys.sf.ClientesRepository;
import com.proyecto.servicios.repositorys.sf.CuentaRepository;
import com.proyecto.servicios.repositorys.sf.DomicilioRepository;
import com.proyecto.servicios.repositorys.sf.UsuarioRepository;
import com.proyecto.servicios.service.AutorizacionService;
import com.proyecto.servicios.service.CatalogoService;
import com.proyecto.servicios.service.ClienteService;
import com.proyecto.servicios.service.exception.ClienteNoEncontradoException;
import com.proyecto.servicios.service.exception.CorreoDuplicadoException;
import com.proyecto.servicios.service.exception.DatosDuplicadosException;
import com.proyecto.servicios.service.exception.NegocioException;
import com.proyecto.servicios.service.exception.UsuarioNoEncontradoException;
import com.proyecto.servicios.service.exception.ValidacionException;
import com.proyecto.servicios.utils.Texto;
import com.proyecto.servicios.validation.PasswordValidator;
import jakarta.persistence.EntityManager;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Alta, consulta, actualización parcial y baja de clientes. Los filtros de consulta viven en
 * {@link ClienteConsultaSpecification} y la fusión de PATCH en {@link CambiosParciales}.
 */
@Service
@Transactional(readOnly = true)
public class ClienteServiceImpl implements ClienteService {

    private static final ZoneId ZONA = ZoneId.of("America/Mexico_City");

    private final ClientesRepository clientes;
    private final DomicilioRepository domicilios;
    private final CuentaRepository cuentas;
    private final UsuarioRepository usuarios;
    private final PasswordEncoder encoder;
    private final EntityManager em;
    private final ObjectMapper mapper;
    private final Validator validator;
    private final AutorizacionService autorizacion;
    private final CatalogoService catalogos;

    public ClienteServiceImpl(ClientesRepository clientes, DomicilioRepository domicilios,
            CuentaRepository cuentas, UsuarioRepository usuarios, PasswordEncoder encoder, EntityManager em,
            ObjectMapper mapper, Validator validator, AutorizacionService autorizacion,
            CatalogoService catalogos) {
        this.clientes = clientes;
        this.domicilios = domicilios;
        this.cuentas = cuentas;
        this.usuarios = usuarios;
        this.encoder = encoder;
        this.em = em;
        this.mapper = mapper;
        this.validator = validator;
        this.autorizacion = autorizacion;
        this.catalogos = catalogos;
    }

    @Override
    @Transactional(readOnly = true)
    public List<String> datosDuplicados(ClienteDisponibilidadRequest r) {
        List<String> duplicados = new ArrayList<>();
        if (r.curp() != null && clientes.existsByCurp(Texto.identidad(r.curp()))) {
            duplicados.add(DatosDuplicadosException.CURP);
        }
        if (r.rfc() != null && clientes.existsByRfc(Texto.identidad(r.rfc()))) {
            duplicados.add(DatosDuplicadosException.RFC);
        }
        if (r.telefonoMovil() != null && clientes.existsByTelefonoMovil(r.telefonoMovil())) {
            duplicados.add(DatosDuplicadosException.TELEFONO);
        }
        if (r.correoElectronico() != null
                && clientes.existsByCorreoElectronicoIgnoreCase(Texto.correo(r.correoElectronico()))) {
            duplicados.add(DatosDuplicadosException.CORREO);
        }
        return duplicados;
    }

    @Override
    @Transactional
    public ClienteResponse registrar(ClienteRequest r) {
        PasswordValidator.validar(r.password());
        validarEdad(r.fechaNacimiento());
        String curp = Texto.identidad(r.curp());
        String rfc = Texto.identidad(r.rfc());
        String correo = Texto.correo(r.correoElectronico());
        List<String> duplicados = datosDuplicados(
                new ClienteDisponibilidadRequest(curp, rfc, r.telefonoMovil(), correo));
        if (!duplicados.isEmpty()) {
            throw new DatosDuplicadosException(duplicados);
        }
        Cliente cliente = new Cliente();
        cliente.setCurp(curp);
        cliente.setRfc(rfc);
        copiarDatos(cliente, r);
        clientes.saveAndFlush(cliente);

        Domicilio domicilio = null;
        if (r.domicilio() != null) {
            domicilio = nuevoDomicilio(cliente, r.domicilio());
        }

        Cuenta cuenta = new Cuenta();
        cuenta.setCliente(cliente);
        cuenta.setNumeroCuenta(cuentas.siguienteNumero());
        cuenta.setSaldo(BigDecimal.ZERO);
        cuentas.save(cuenta);

        Usuario usuario = new Usuario();
        usuario.setCliente(cliente);
        usuario.setCorreo(correo);
        usuario.setPasswordHash(encoder.encode(r.password()));
        usuarios.save(usuario);

        em.flush();
        em.refresh(cliente);
        if (domicilio != null) {
            em.refresh(domicilio);
        }
        em.refresh(cuenta);
        em.refresh(usuario);
        return ClienteMapper.cliente(cliente, domicilio, usuario, List.of(cuenta));
    }

    @Override
    public ListaGenericResponse<ClienteResponse> consultar(ClienteConsultaRequest request,
            Integer usuarioAutenticadoId) {
        validarEntrada(request);
        Integer propietarioId = autorizacion.clienteIdParaConsulta(usuarioAutenticadoId);
        Page<Cliente> pagina = clientes.findAll(ClienteConsultaSpecification.de(request, propietarioId),
                PageRequest.of(request.getPagina(), request.getTamanio(), Sort.by("id")));
        if (pagina.isEmpty()) {
            return ListaGenericResponse.desde(pagina.map(this::respuesta));
        }
        // Cargar las relaciones de toda la página evita consultas adicionales por cada cliente.
        List<Integer> ids = pagina.stream().map(Cliente::getId).toList();
        Map<Integer, Domicilio> domiciliosPorCliente = domicilios.findByClienteIdIn(ids).stream()
                .collect(Collectors.toMap(d -> d.getCliente().getId(), Function.identity()));
        Map<Integer, Usuario> usuariosPorCliente = usuarios.findByClienteIdIn(ids).stream()
                .collect(Collectors.toMap(u -> u.getCliente().getId(), Function.identity()));
        Map<Integer, List<Cuenta>> cuentasPorCliente = cuentas.findByClienteIdInOrderByIdAsc(ids).stream()
                .collect(Collectors.groupingBy(c -> c.getCliente().getId()));
        return ListaGenericResponse.desde(pagina.map(cliente -> {
            Domicilio domicilio = domiciliosPorCliente.get(cliente.getId());
            Usuario usuario = usuariosPorCliente.get(cliente.getId());
            if (usuario == null) {
                throw new UsuarioNoEncontradoException();
            }
            return ClienteMapper.cliente(cliente, domicilio, usuario,
                    cuentasPorCliente.getOrDefault(cliente.getId(), List.of()));
        }));
    }

    @Override
    @Transactional
    public ClienteResponse actualizarParcial(ClienteActualizacionPorIdentidadRequest request,
            Integer usuarioAutenticadoId) {
        validarEntrada(request);
        JsonNode cambios = request.cambios();
        Cliente identificado = buscar(request.identificacion());
        autorizacion.autorizarCliente(identificado.getId(), usuarioAutenticadoId);
        Integer id = identificado.getId();
        Cliente cliente = bloquear(id);
        Domicilio domicilio = domicilios.findByClienteId(id).orElse(null);
        ObjectNode combinado = mapper.valueToTree(actualActualizacion(cliente, domicilio));
        CambiosParciales.combinar(combinado, cambios);
        try {
            ClienteActualizacionRequest actualizacion = mapper.treeToValue(combinado,
                    ClienteActualizacionRequest.class);
            validarEntrada(actualizacion);
            return aplicarActualizacion(cliente, actualizacion);
        } catch (JsonProcessingException exception) {
            throw new ValidacionException("Solicitud inválida: revisa los tipos y valores de los campos");
        }
    }

    @Override
    @Transactional
    public void desactivar(ClienteIdentificacionRequest request, Integer usuarioAutenticadoId) {
        validarEntrada(request);
        Cliente identificado = buscar(request);
        autorizacion.autorizarCliente(identificado.getId(), usuarioAutenticadoId);
        bloquear(identificado.getId()).setActivo(false);
        // Los triggers desactivan usuario y cuentas en esta misma transacción.
        em.flush();
        em.clear();
    }

    @Override
    @Transactional
    public void activar(ClienteIdentificacionRequest request, Integer usuarioAutenticadoId) {
        validarEntrada(request);
        autorizacion.exigirAdministrador(usuarioAutenticadoId);
        Cliente cliente = bloquear(buscar(request).getId());
        Integer id = cliente.getId();
        cliente.setActivo(true);
        // Los triggers solo propagan la baja: el alta devuelve a mano el acceso y las cuentas.
        // El cliente se escribe antes porque usuario y cuenta activos exigen un cliente activo.
        em.flush();
        usuarios.findByClienteId(id).ifPresent(u -> u.setActivo(true));
        cuentas.findByClienteIdOrderByIdAsc(id).forEach(c -> c.setEstaActiva(true));
        em.flush();
        em.clear();
    }

    private ClienteResponse aplicarActualizacion(Cliente cliente, ClienteActualizacionRequest r) {
        Integer id = cliente.getId();
        validarEdad(r.fechaNacimiento());
        if (!cliente.isActivo()) {
            throw new NegocioException(HttpStatus.CONFLICT, "El cliente está inactivo");
        }
        if (clientes.existsByCorreoElectronicoIgnoreCaseAndIdNot(Texto.correo(r.correoElectronico()), id)) {
            throw new CorreoDuplicadoException();
        }
        if (clientes.existsByTelefonoMovilAndIdNot(r.telefonoMovil(), id)) {
            throw new DatosDuplicadosException(List.of(DatosDuplicadosException.TELEFONO));
        }
        copiarDatos(cliente, r);
        if (r.domicilio() != null) {
            Domicilio domicilio = domicilios.findByClienteId(id).orElse(null);
            if (domicilio == null) {
                nuevoDomicilio(cliente, r.domicilio());
            } else {
                aplicarDomicilio(domicilio, r.domicilio());
            }
        }
        // El trigger sincroniza el correo del usuario. Vaciar el contexto evita datos obsoletos.
        em.flush();
        em.clear();
        return respuesta(buscar(id));
    }

    /** Estado vigente del cliente con la forma de una actualización completa, base del PATCH. */
    private ClienteActualizacionRequest actualActualizacion(Cliente c, Domicilio d) {
        return new ClienteActualizacionRequest(
                c.getPrimerNombre(), c.getSegundoNombre(), c.getApellidoPaterno(), c.getApellidoMaterno(),
                c.getFechaNacimiento(), c.getSexo(), c.getNacionalidad(), c.getEstadoCivil(),
                c.getCorreoElectronico(), c.getTelefonoMovil(), c.getTelefonoAlternativo(),
                c.getOcupacion(), c.getEmpresa(), c.getIngresoMensual(),
                d == null ? null
                        : new DomicilioRequest(d.getCalle(), d.getNumeroExterior(), d.getNumeroInterior(),
                                d.getColonia(), d.getMunicipio(), d.getEstado(), d.getCodigoPostal(), d.getPais()));
    }

    private Cliente buscar(Integer id) {
        return clientes.findById(id).orElseThrow(ClienteNoEncontradoException::new);
    }

    private Cliente bloquear(Integer id) {
        Cliente cliente = clientes.bloquearPorId(id).orElseThrow(ClienteNoEncontradoException::new);
        // La búsqueda por identidad puede haber cargado la entidad antes de esperar el bloqueo.
        em.refresh(cliente);
        return cliente;
    }

    private Cliente buscar(ClienteIdentificacionRequest request) {
        if (request.rfc() != null && request.curp() != null) {
            return clientes.findByRfcIgnoreCaseAndCurpIgnoreCase(Texto.identidad(request.rfc()),
                    Texto.identidad(request.curp())).orElseThrow(ClienteNoEncontradoException::new);
        }
        if (request.rfc() != null) {
            return clientes.findByRfcIgnoreCase(Texto.identidad(request.rfc()))
                    .orElseThrow(ClienteNoEncontradoException::new);
        }
        return clientes.findByCurpIgnoreCase(Texto.identidad(request.curp()))
                .orElseThrow(ClienteNoEncontradoException::new);
    }

    private void validarEntrada(Object request) {
        if (request == null) {
            throw new ValidacionException("La solicitud es obligatoria");
        }
        var errores = validator.validate(request);
        if (!errores.isEmpty()) {
            throw new ConstraintViolationException(errores);
        }
    }

    private ClienteResponse respuesta(Cliente c) {
        Domicilio d = domicilios.findByClienteId(c.getId()).orElse(null);
        Usuario u = usuarios.findByClienteId(c.getId()).orElseThrow(UsuarioNoEncontradoException::new);
        return ClienteMapper.cliente(c, d, u, cuentas.findByClienteIdOrderByIdAsc(c.getId()));
    }

    /** Copia los datos que el cliente puede capturar o modificar (no incluye RFC ni CURP). */
    private void copiarDatos(Cliente c, DatosCliente r) {
        c.setPrimerNombre(r.primerNombre().trim());
        c.setSegundoNombre(Texto.opcional(r.segundoNombre()));
        c.setApellidoPaterno(r.apellidoPaterno().trim());
        c.setApellidoMaterno(r.apellidoMaterno().trim());
        c.setFechaNacimiento(r.fechaNacimiento());
        c.setSexo(r.sexo());
        c.setNacionalidad(delCatalogo(r.nacionalidad(), c.getNacionalidad(), catalogos::existeNacionalidad,
                "una nacionalidad"));
        c.setEstadoCivil(r.estadoCivil());
        c.setCorreoElectronico(Texto.correo(r.correoElectronico()));
        c.setTelefonoMovil(r.telefonoMovil());
        c.setTelefonoAlternativo(r.telefonoAlternativo());
        String ocupacion = Texto.opcional(r.ocupacion());
        c.setOcupacion(ocupacion == null ? null
                : delCatalogo(ocupacion, c.getOcupacion(), catalogos::existeOcupacion, "una ocupación"));
        c.setEmpresa(Texto.opcional(r.empresa()));
        c.setIngresoMensual(r.ingresoMensual());
    }

    private Domicilio nuevoDomicilio(Cliente cliente, DomicilioRequest r) {
        Domicilio domicilio = new Domicilio();
        domicilio.setCliente(cliente);
        aplicarDomicilio(domicilio, r);
        return domicilios.save(domicilio);
    }

    private void aplicarDomicilio(Domicilio d, DomicilioRequest r) {
        d.setCalle(r.calle().trim());
        d.setNumeroExterior(r.numeroExterior().trim());
        d.setNumeroInterior(Texto.opcional(r.numeroInterior()));
        d.setColonia(r.colonia().trim());
        d.setMunicipio(r.municipio().trim());
        d.setEstado(r.estado().trim());
        d.setCodigoPostal(r.codigoPostal());
        d.setPais(delCatalogo(r.pais(), d.getPais(), catalogos::existePais, "un país"));
    }

    /** Un valor ya guardado (aunque sea anterior al catálogo) se conserva; uno nuevo debe estar en el catálogo. */
    private static String delCatalogo(String nuevo, String actual, Predicate<String> enCatalogo, String campo) {
        String valor = nuevo.trim();
        if (!valor.equals(actual) && !enCatalogo.test(valor)) {
            throw new ValidacionException("Elige " + campo + " de la lista");
        }
        return valor;
    }

    private void validarEdad(LocalDate fecha) {
        if (fecha == null || fecha.isAfter(LocalDate.now(ZONA).minusYears(18))) {
            throw new ValidacionException("El cliente debe tener al menos 18 años y la fecha no puede ser futura");
        }
    }
}
