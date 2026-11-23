package com.proyecto.servicios.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.util.ReflectionTestUtils;

class FlywayConfigTest {

    @Test
    void aplicaLasMigracionesDeLaUbicacionConfigurada() {
        DataSource ds = new DriverManagerDataSource("jdbc:h2:mem:flyway_test;DB_CLOSE_DELAY=-1", "sa", "");
        FlywayConfig config = new FlywayConfig();
        ReflectionTestUtils.setField(config, "locations", new String[] {"classpath:db/prueba"});
        ReflectionTestUtils.setField(config, "historyTable", "historial");
        ReflectionTestUtils.setField(config, "schema", "PUBLIC");
        ReflectionTestUtils.setField(config, "baselineVersion", "0");

        Flyway flyway = config.flyway(ds);

        assertEquals("1", flyway.info().current().getVersion().getVersion());
        assertEquals(0, new JdbcTemplate(ds).queryForObject("SELECT COUNT(*) FROM tabla_de_prueba", Integer.class));
    }
}
