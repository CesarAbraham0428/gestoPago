package com.proyecto.servicios.integration;

import com.proyecto.servicios.service.TotpService;
import com.proyecto.servicios.utils.CifradoTokenConverter;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Hace de app autenticadora en las pruebas de integración: lee el secreto del usuario y calcula su código. */
@Component
public class CodigosDePrueba {
    private final JdbcTemplate jdbc;
    private final CifradoTokenConverter cifrado;
    private final TotpService totp;
    private final RelojDePrueba reloj;

    public CodigosDePrueba(DataSource datasource, CifradoTokenConverter cifrado, TotpService totp,
            RelojDePrueba reloj) {
        this.jdbc = new JdbcTemplate(datasource);
        this.cifrado = cifrado;
        this.totp = totp;
        this.reloj = reloj;
    }

    /** Código de un paso de tiempo nuevo (nunca usado) de la app del usuario. Su secreto ya debe existir. */
    public String nuevoCodigo(String correo) {
        reloj.avanzarUnPaso();
        String secreto = cifrado.convertToEntityAttribute(jdbc.queryForObject(
                "select totp_secreto from usuarios where lower(correo) = lower(?)", String.class, correo));
        return totp.codigo(secreto, totp.pasoActual());
    }
}
