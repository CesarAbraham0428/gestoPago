package com.proyecto.servicios.service;
import com.proyecto.servicios.entity.sf.*;
import com.proyecto.servicios.model.LoginRequest;
import com.proyecto.servicios.repositorys.sf.UsuarioRepository;
import com.proyecto.servicios.security.JwtTokenService;
import com.proyecto.servicios.service.Impl.AutenticacionServiceImpl;
import com.proyecto.servicios.service.exception.AutenticacionException;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
@ExtendWith(MockitoExtension.class)
class AutenticacionServiceTest {
    @Mock UsuarioRepository usuarios;
    @Mock JwtTokenService tokens;
    final BCryptPasswordEncoder encoder=new BCryptPasswordEncoder(4);
    AutenticacionService service;
    @BeforeEach void preparar() { service=new AutenticacionServiceImpl(usuarios,encoder,tokens); }
    private Usuario usuario() {
        Cliente c=new Cliente(); c.setPrimerNombre("Ana"); c.setApellidoPaterno("Pérez"); c.setApellidoMaterno("Ruiz");
        Usuario u=new Usuario(); u.setId(7); u.setCorreo("ana@example.com"); u.setCliente(c);
        u.setPasswordHash(encoder.encode("Segura123!")); return u;
    }
    @Test void loginNormalizaCorreoYUsaIdEstable() {
        when(usuarios.findByCorreoIgnoreCase("ana@example.com")).thenReturn(Optional.of(usuario()));
        when(tokens.emitir("7")).thenReturn("jwt");
        when(tokens.getExpirationMs()).thenReturn(60000L);
        var r=service.iniciarSesion(new LoginRequest(" ANA@EXAMPLE.COM ","Segura123!"));
        assertEquals("ana@example.com",r.usuario()); assertEquals("Ana Pérez Ruiz",r.nombreCompleto()); assertEquals("jwt",r.token());
        assertEquals("Bearer", r.tipo());
        assertEquals(60000, r.expiraEnMs());
    }
    @Test void passwordIncorrectoNoEmiteToken() {
        when(usuarios.findByCorreoIgnoreCase("ana@example.com")).thenReturn(Optional.of(usuario()));
        assertEquals(AutenticacionException.Tipo.CREDENCIALES_INVALIDAS,assertThrows(AutenticacionException.class,
            () -> service.iniciarSesion(new LoginRequest("ana@example.com","incorrecta"))).getTipo());
        verifyNoInteractions(tokens);
    }
    @Test void clienteInactivoNoPuedeIniciarSesion() {
        Usuario u=usuario(); u.getCliente().setActivo(false);
        when(usuarios.findByCorreoIgnoreCase("ana@example.com")).thenReturn(Optional.of(u));
        assertEquals(AutenticacionException.Tipo.CUENTA_INACTIVA,assertThrows(AutenticacionException.class,
            () -> service.iniciarSesion(new LoginRequest("ana@example.com","Segura123!"))).getTipo());
        verifyNoInteractions(tokens);
    }
    @Test void usuarioInactivoNoPuedeIniciarSesion() {
        Usuario u=usuario(); u.setActivo(false);
        when(usuarios.findByCorreoIgnoreCase("ana@example.com")).thenReturn(Optional.of(u));
        assertEquals(AutenticacionException.Tipo.CUENTA_INACTIVA, assertThrows(AutenticacionException.class,
            () -> service.iniciarSesion(new LoginRequest("ana@example.com","Segura123!"))).getTipo());
        verifyNoInteractions(tokens);
    }
    @Test void usuarioInexistenteNoEmiteToken() {
        when(usuarios.findByCorreoIgnoreCase("nadie@example.com")).thenReturn(Optional.empty());
        var exception = assertThrows(AutenticacionException.class,
            () -> service.iniciarSesion(new LoginRequest("nadie@example.com", "Segura123!")));
        assertEquals(AutenticacionException.Tipo.CREDENCIALES_INVALIDAS, exception.getTipo());
        verifyNoInteractions(tokens);
    }
}
