package com.proyecto.servicios.service.Impl;

import com.proyecto.servicios.entity.sf.Cliente;
import com.proyecto.servicios.entity.sf.Usuario;
import com.proyecto.servicios.models.request.LoginRequest;
import com.proyecto.servicios.models.request.VerificacionCodigoRequest;
import com.proyecto.servicios.models.response.AuthResponse;
import com.proyecto.servicios.models.response.DesafioResponse;
import com.proyecto.servicios.models.response.DesafioResponse.Enrolamiento;
import com.proyecto.servicios.repositorys.sf.UsuarioRepository;
import com.proyecto.servicios.security.JwtTokenService;
import com.proyecto.servicios.security.JwtTokenService.TokenEmitido;
import com.proyecto.servicios.service.AutenticacionService;
import com.proyecto.servicios.service.CodigoVerificacionService;
import com.proyecto.servicios.service.CodigoVerificacionService.Desafio;
import com.proyecto.servicios.service.CodigoVerificacionService.Verificado;
import com.proyecto.servicios.service.SesionService;
import com.proyecto.servicios.service.TotpService;
import com.proyecto.servicios.service.exception.AutenticacionException;
import com.proyecto.servicios.service.exception.AutenticacionException.Tipo;
import com.proyecto.servicios.service.exception.NegocioException;
import com.proyecto.servicios.utils.Texto;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Inicio de sesión en dos pasos: 1) usuario y contraseña, 2) código de la app autenticadora (TOTP). Solo después del
 * segundo paso se emite el JWT y se registra la sesión única del usuario.
 */
@Service
public class AutenticacionServiceImpl implements AutenticacionService {

    private final UsuarioRepository usuarios;
    private final PasswordEncoder encoder;
    private final JwtTokenService tokens;
    private final CodigoVerificacionService codigos;
    private final SesionService sesiones;
    private final TotpService totp;
    /** Se compara contra este hash cuando el correo no existe, para que la respuesta tarde lo mismo. */
    private final String hashFicticio;

    public AutenticacionServiceImpl(UsuarioRepository usuarios, PasswordEncoder encoder, JwtTokenService tokens,
            CodigoVerificacionService codigos, SesionService sesiones, TotpService totp) {
        this.usuarios = usuarios;
        this.encoder = encoder;
        this.tokens = tokens;
        this.codigos = codigos;
        this.sesiones = sesiones;
        this.totp = totp;
        this.hashFicticio = encoder.encode(UUID.randomUUID().toString());
    }

    @Override
    @Transactional
    public DesafioResponse iniciarSesion(LoginRequest r) {
        Optional<Usuario> encontrado = usuarios.findByCorreoIgnoreCase(Texto.correo(r.correo()));
        boolean coincide = encoder.matches(r.password(),
                encontrado.map(Usuario::getPasswordHash).orElse(hashFicticio));
        if (encontrado.isEmpty() || !coincide) {
            throw new AutenticacionException(Tipo.CREDENCIALES_INVALIDAS);
        }
        Usuario u = encontrado.get();
        validarActivo(u);
        Enrolamiento enrolamiento = null;
        if (!u.isTotpConfirmado()) {
            // Se conserva el secreto aún sin confirmar: regenerarlo invalidaría una app ya escaneada.
            if (u.getTotpSecreto() == null) {
                u.setTotpSecreto(totp.nuevoSecreto());
            }
            enrolamiento = new Enrolamiento(u.getTotpSecreto(), totp.uri(u.getCorreo(), u.getTotpSecreto()));
        }
        Desafio desafio = codigos.emitir(u.getId());
        return new DesafioResponse(desafio.id(), desafio.vigencia().toMillis(), enrolamiento);
    }

    @Override
    @Transactional(noRollbackFor = {NegocioException.class, AutenticacionException.class})
    public AuthResponse verificarCodigo(VerificacionCodigoRequest r) {
        Verificado verificado = codigos.verificar(r.desafioId(), r.codigo());
        Usuario u = verificado.usuario();
        Integer usuarioId = u.getId();
        validarActivo(u);

        TokenEmitido token = tokens.emitir(usuarioId.toString());
        if (!sesiones.abrir(usuarioId, token, r.cerrarSesionAnterior())) {
            // El desafío queda verificado: el cliente confirma y reenvía sin necesitar otro código.
            throw new NegocioException(HttpStatus.CONFLICT,
                    "Ya tienes una sesión activa en otro dispositivo. Ciérrala para continuar aquí");
        }
        // Solo ahora el código cuenta como usado y la app como enlazada.
        u.setTotpUltimoPaso(verificado.paso());
        u.setTotpConfirmado(true);
        codigos.consumir(r.desafioId());
        return new AuthResponse(token.token(), "Bearer", tokens.getExpirationMs(), u.getCorreo(),
                nombreCompleto(u.getCliente()));
    }

    @Override
    public void cerrarSesion(Integer usuarioId) {
        sesiones.cerrar(usuarioId);
    }

    private static void validarActivo(Usuario u) {
        if (!u.isActivo() || !u.getCliente().isActivo()) {
            throw new AutenticacionException(Tipo.CUENTA_INACTIVA);
        }
    }

    private static String nombreCompleto(Cliente c) {
        return Stream.of(c.getPrimerNombre(), c.getSegundoNombre(), c.getApellidoPaterno(), c.getApellidoMaterno())
                .filter(Objects::nonNull).reduce((a, b) -> a + " " + b).orElse("");
    }
}
