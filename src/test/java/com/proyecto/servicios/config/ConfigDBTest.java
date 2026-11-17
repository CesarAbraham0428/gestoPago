package com.proyecto.servicios.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

import com.zaxxer.hikari.HikariDataSource;
import jakarta.persistence.EntityManagerFactory;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;

class ConfigDBTest {

    private MockEnvironment entorno() {
        return new MockEnvironment()
                .withProperty("spring.datasource.url", "jdbc:h2:mem:config_db_test;DB_CLOSE_DELAY=-1")
                .withProperty("spring.datasource.username", "sa")
                .withProperty("spring.datasource.password", "");
    }

    @Test
    void creaElPoolConLosValoresPorOmision() {
        try (HikariDataSource ds = (HikariDataSource) new ConfigDB(entorno()).sfDatasource()) {
            assertEquals("sfDatasource", ds.getPoolName());
            assertEquals(10, ds.getMaximumPoolSize());
            assertEquals(2, ds.getMinimumIdle());
            assertEquals(5000L, ds.getConnectionTimeout());
        }
    }

    @Test
    void respetaLosValoresConfigurados() {
        MockEnvironment env = entorno()
                .withProperty("spring.datasource.hikari.maximum-pool-size", "4")
                .withProperty("spring.datasource.hikari.minimum-idle", "1");
        try (HikariDataSource ds = (HikariDataSource) new ConfigDB(env).sfDatasource()) {
            assertEquals(4, ds.getMaximumPoolSize());
        }
    }

    @Test
    void sinUrlNoArranca() {
        ConfigDB config = new ConfigDB(new MockEnvironment());
        assertThrows(IllegalStateException.class, config::sfDatasource);
    }

    @Test
    void configuraElFactoryDeEntidadesSinCrearEsquema() {
        DataSource ds = mock(DataSource.class);

        LocalContainerEntityManagerFactoryBean factory = new ConfigDB(entorno())
                .sfEntityManagerFactory(ds, mock(ConfigurableListableBeanFactory.class));

        assertSame(ds, factory.getDataSource());
        assertEquals("none", factory.getJpaPropertyMap().get("hibernate.hbm2ddl.auto"));
        assertEquals(10000, factory.getJpaPropertyMap().get("jakarta.persistence.query.timeout"));
    }

    @Test
    void creaElAdministradorDeTransacciones() {
        assertInstanceOf(JpaTransactionManager.class,
                new ConfigDB(entorno()).sfTransactionManager(mock(EntityManagerFactory.class)));
    }
}
