package com.proyecto.servicios.service;
import com.proyecto.servicios.entity.sf.*;
import com.proyecto.servicios.model.LoginRequest;
import com.proyecto.servicios.repositorys.sf.UsuarioRepository;
import com.proyecto.servicios.security.JwtTokenService;
import com.proyecto.servicios.service.Impl.AutenticacionServiceImpl;
import com.proyecto.servicios.service.exception.AutenticacionException;
import com.proyecto.servicios.validation.PasswordValidator;
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
        var r=service.iniciarSesion(new LoginRequest(" ANA@EXAMPLE.COM ","Segura123!"));
        assertEquals("ana@example.com",r.usuario()); assertEquals("Ana Pérez Ruiz",r.nombreCompleto()); assertEquals("jwt",r.token());
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
        assertThrows(AutenticacionException.class,() -> service.iniciarSesion(new LoginRequest("ana@example.com","Segura123!")));
        verifyNoInteractions(tokens);
    }
    @Test void passwordExigeComplejidadYLimiteDeBytes() {
        assertDoesNotThrow(() -> PasswordValidator.validar("Segura123!"));
        for(String p:new String[]{"Ab1!","segura123!","SEGURA123!","Seguraabc!","Segura123","Aa1!"+"ñ".repeat(35)})
            assertThrows(RuntimeException.class,() -> PasswordValidator.validar(p));
    }
}
