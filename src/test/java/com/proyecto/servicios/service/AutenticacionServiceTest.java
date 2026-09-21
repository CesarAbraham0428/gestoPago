package com.proyecto.servicios.service;

import com.proyecto.servicios.entity.sf.Personas;
import com.proyecto.servicios.entity.sf.Registro;
import com.proyecto.servicios.model.AuthResponse;
import com.proyecto.servicios.model.LoginRequest;
import com.proyecto.servicios.model.RegistroRequest;
import com.proyecto.servicios.repositorys.sf.PersonasRepository;
import com.proyecto.servicios.repositorys.sf.RegistroRepository;
import com.proyecto.servicios.security.JwtTokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AutenticacionServiceTest {

    @Mock
    private RegistroRepository registroRepository;
    @Mock
    private PersonasRepository personasRepository;
    @Mock
    private JwtTokenService jwtTokenService;

    private BCryptPasswordEncoder passwordEncoder;
    private AutenticacionService service;

    @BeforeEach
    void setUp() {
        passwordEncoder = new BCryptPasswordEncoder();
        service = new AutenticacionService(registroRepository, personasRepository, passwordEncoder, jwtTokenService);
    }

    @Test
    void registrar_normalizaUsuarioYGuardaHashBcrypt() {
        RegistroRequest request = registrationRequest("  Alice.User  ", "  Ana  ", "  PEREZ ", " Ruiz ");
        when(registroRepository.existsByUsuarioIgnoreCase("alice.user")).thenReturn(false);
        when(jwtTokenService.emitir("alice.user")).thenReturn("jwt-token");
        when(jwtTokenService.getExpirationMs()).thenReturn(28_800_000L);

        AuthResponse result = service.registrar(request);

        assertEquals("alice.user", result.usuario());
        assertEquals("jwt-token", result.token());
        assertEquals("Bearer", result.tipo());
        assertEquals("Ana PEREZ Ruiz", result.nombreCompleto());
        verify(registroRepository).existsByUsuarioIgnoreCase("alice.user");

        ArgumentCaptor<Personas> personaCaptor = ArgumentCaptor.forClass(Personas.class);
        verify(personasRepository).save(personaCaptor.capture());
        assertEquals("Ana", personaCaptor.getValue().getNombre());
        assertEquals("PEREZ", personaCaptor.getValue().getApellidoP());
        assertEquals("Ruiz", personaCaptor.getValue().getApellidoMaterno());
        assertEquals("alice@example.com", personaCaptor.getValue().getCorreo());

        ArgumentCaptor<Registro> registroCaptor = ArgumentCaptor.forClass(Registro.class);
        verify(registroRepository).save(registroCaptor.capture());
        Registro persisted = registroCaptor.getValue();
        assertEquals("alice.user", persisted.getUsuario());
        assertTrue(persisted.isActivo());
        assertNotEquals("correct-horse-battery", persisted.getPasswordHash());
        assertTrue(persisted.getPasswordHash().startsWith("$2a$")
                || persisted.getPasswordHash().startsWith("$2b$")
                || persisted.getPasswordHash().startsWith("$2y$"));
        assertTrue(passwordEncoder.matches("correct-horse-battery", persisted.getPasswordHash()));
        assertEquals(personaCaptor.getValue(), persisted.getPersona());
    }

    @Test
    void registrar_usuarioDuplicadoLanzaExcepcionSinCrearPersona() {
        when(registroRepository.existsByUsuarioIgnoreCase("alice.user")).thenReturn(true);

        assertThrows(
                AutenticacionService.UsuarioDuplicadoException.class,
                () -> service.registrar(registrationRequest(" Alice.User ", "Ana", "Perez", "Ruiz")));

        verifyNoInteractions(personasRepository, jwtTokenService);
        verify(registroRepository, never()).save(any(Registro.class));
    }

    @Test
    void iniciarSesion_credencialesValidasDevuelvenRespuestaDeAutenticacion() {
        Registro registro = activeRegistration("alice.user", "correct-horse-battery");
        when(registroRepository.findByUsuarioIgnoreCase("alice.user")).thenReturn(Optional.of(registro));
        when(jwtTokenService.emitir("alice.user")).thenReturn("jwt-token");
        when(jwtTokenService.getExpirationMs()).thenReturn(28_800_000L);

        AuthResponse result = service.iniciarSesion(new LoginRequest(" alice.user ", "correct-horse-battery"));

        assertEquals("jwt-token", result.token());
        assertEquals("Bearer", result.tipo());
        assertEquals(28_800_000L, result.expiraEnMs());
        assertEquals("alice.user", result.usuario());
        assertEquals("Ana Perez Ruiz", result.nombreCompleto());
        verify(registroRepository).findByUsuarioIgnoreCase("alice.user");
        verify(jwtTokenService).emitir("alice.user");
    }

    @Test
    void iniciarSesion_conPasswordIncorrectoLanzaExcepcion401Esperada() {
        Registro registro = activeRegistration("alice.user", "correct-horse-battery");
        when(registroRepository.findByUsuarioIgnoreCase("alice.user")).thenReturn(Optional.of(registro));

        assertThrows(
                AutenticacionService.CredencialesInvalidasException.class,
                () -> service.iniciarSesion(new LoginRequest("alice.user", "wrong-password")));

        verifyNoInteractions(jwtTokenService);
    }

    @Test
    void iniciarSesion_conCuentaInactivaLanzaExcepcion403Esperada() {
        Registro registro = activeRegistration("alice.user", "correct-horse-battery");
        registro.setActivo(false);
        when(registroRepository.findByUsuarioIgnoreCase("alice.user")).thenReturn(Optional.of(registro));

        assertThrows(
                AutenticacionService.CuentaInactivaException.class,
                () -> service.iniciarSesion(new LoginRequest("alice.user", "correct-horse-battery")));

        verifyNoInteractions(jwtTokenService);
    }

    private RegistroRequest registrationRequest(
            String username, String firstName, String lastName, String maternalName) {
        return new RegistroRequest(
                username,
                "correct-horse-battery",
                firstName,
                lastName,
                maternalName,
                " ALICE@EXAMPLE.COM ",
                "+5215555555555");
    }

    private Registro activeRegistration(String username, String rawPassword) {
        Personas persona = new Personas();
        persona.setNombre("Ana");
        persona.setApellidoP("Perez");
        persona.setApellidoMaterno("Ruiz");
        persona.setCorreo("alice@example.com");
        persona.setTelefono("+5215555555555");

        Registro registro = new Registro();
        registro.setUsuario(username);
        registro.setPasswordHash(passwordEncoder.encode(rawPassword));
        registro.setActivo(true);
        registro.setPersona(persona);
        return registro;
    }
}
