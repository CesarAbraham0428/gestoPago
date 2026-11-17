package com.proyecto.servicios.service.Impl;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class EnmascararCorreoTest {

    @Test
    void elCorreoSeEnmascaraConservandoElDominio() {
        assertEquals("a***@correo.com", AutenticacionServiceImpl.enmascarar("ana@correo.com"));
        assertEquals("***", AutenticacionServiceImpl.enmascarar("sin-arroba"));
        assertEquals("***", AutenticacionServiceImpl.enmascarar("@correo.com"));
    }
}
