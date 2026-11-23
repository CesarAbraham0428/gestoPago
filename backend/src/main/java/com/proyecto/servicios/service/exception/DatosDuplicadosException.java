package com.proyecto.servicios.service.exception;

import java.util.List;
import java.util.Map;

/** Datos únicos del cliente que ya pertenecen a otra cuenta; el mensaje nombra cada uno. */
public class DatosDuplicadosException extends ClienteYaRegistradoException {
    public static final String CURP = "curp";
    public static final String RFC = "rfc";
    public static final String TELEFONO = "telefonoMovil";
    public static final String CORREO = "correoElectronico";

    private static final Map<String, String> ETIQUETAS = Map.of(
            CURP, "CURP", RFC, "RFC", TELEFONO, "teléfono móvil", CORREO, "correo electrónico");

    public DatosDuplicadosException(List<String> campos) { super(mensaje(campos)); }

    static String mensaje(List<String> campos) {
        List<String> nombres = campos.stream().map(ETIQUETAS::get).toList();
        if (nombres.size() == 1) {
            return "El " + nombres.get(0) + " ya está registrado";
        }
        int ultimo = nombres.size() - 1;
        return "Ya están registrados: " + String.join(", ", nombres.subList(0, ultimo)) + " y " + nombres.get(ultimo);
    }
}
