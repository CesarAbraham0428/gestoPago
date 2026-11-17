package com.proyecto.servicios.validation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.proyecto.servicios.entity.sf.EstadoCivil;
import com.proyecto.servicios.entity.sf.EstadoCivilConverter;
import com.proyecto.servicios.models.response.CatalogoProductosResponse;
import com.proyecto.servicios.models.response.ProductoResponse;
import java.util.List;
import org.junit.jupiter.api.Test;

class ValidadoresTest {

    private final NombreValidator nombre = new NombreValidator();
    private final PasswordSeguraValidator password = new PasswordSeguraValidator();

    private static ProductoResponse producto(String nombre, String servicio, Integer idServicio, Integer idProducto) {
        ProductoResponse p = new ProductoResponse();
        p.setProducto(nombre);
        p.setServicio(servicio);
        p.setIdServicio(idServicio);
        p.setIdProducto(idProducto);
        return p;
    }

    @Test
    void elNombreAceptaNuloLetrasYEspacios() {
        assertTrue(nombre.isValid(null, null));
        assertTrue(nombre.isValid("María José", null));
        assertTrue(nombre.isValid(" Ñu ", null));
    }

    @Test
    void elNombreRechazaCortosLargosYCaracteresInvalidos() {
        assertFalse(nombre.isValid("A", null));
        assertFalse(nombre.isValid("Ana3", null));
        assertFalse(nombre.isValid("A".repeat(51), null));
        assertFalse(nombre.isValid(" " + "A".repeat(50), null));
    }

    @Test
    void laPasswordSeguraAceptaNuloYRechazaLasDebiles() {
        assertTrue(password.isValid(null, null));
        assertTrue(password.isValid("Nueva#Segura2026", null));
        assertFalse(password.isValid("123", null));
    }

    @Test
    void elCatalogoDeProductosSeValidaPorProducto() {
        ProductoResponse bueno = producto("Telcel", "Recargas", 1, 2);

        assertTrue(CatalogoProductosValidator.esValido(List.of(bueno)));
        assertFalse(CatalogoProductosValidator.esValido((List<ProductoResponse>) null));
        assertFalse(CatalogoProductosValidator.esValido(List.of()));
        assertFalse(CatalogoProductosValidator.esValido(java.util.Arrays.asList(bueno, null)));
        assertFalse(CatalogoProductosValidator.esProductoValido(producto(null, "S", 1, 2)));
        assertFalse(CatalogoProductosValidator.esProductoValido(producto(" ", "S", 1, 2)));
        assertFalse(CatalogoProductosValidator.esProductoValido(producto("P", null, 1, 2)));
        assertFalse(CatalogoProductosValidator.esProductoValido(producto("P", " ", 1, 2)));
        assertFalse(CatalogoProductosValidator.esProductoValido(producto("P", "S", null, 2)));
        assertFalse(CatalogoProductosValidator.esProductoValido(producto("P", "S", 1, null)));
    }

    @Test
    void elCatalogoCompletoExigeCodigoCeroYTotalCoherente() {
        List<ProductoResponse> productos = List.of(producto("P", "S", 1, 2));
        assertTrue(CatalogoProductosValidator.esValido(CatalogoProductosResponse.de(0, "ok", productos)));
        assertFalse(CatalogoProductosValidator.esValido((CatalogoProductosResponse) null));
        assertFalse(CatalogoProductosValidator.esValido(CatalogoProductosResponse.de(0, "ok", List.of())));
        assertFalse(CatalogoProductosValidator.esValido(CatalogoProductosResponse.de(1, "x", productos)));

        CatalogoProductosResponse sinTotal = CatalogoProductosResponse.de(0, "ok", productos);
        sinTotal.setTotal(null);
        assertFalse(CatalogoProductosValidator.esValido(sinTotal));
        sinTotal.setTotal(5);
        assertFalse(CatalogoProductosValidator.esValido(sinTotal));
    }

    @Test
    void elEstadoCivilSeConvierteDeIdaYVuelta() {
        EstadoCivilConverter convertidor = new EstadoCivilConverter();

        assertEquals("Union libre", convertidor.convertToDatabaseColumn(EstadoCivil.Union_libre));
        assertEquals(null, convertidor.convertToDatabaseColumn(null));
        assertEquals(EstadoCivil.Casado, convertidor.convertToEntityAttribute("Casado"));
        assertEquals(null, convertidor.convertToEntityAttribute(null));
        assertEquals(EstadoCivil.Viudo, EstadoCivil.parse("Viudo"));
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () -> EstadoCivil.parse("x"));
    }
}
