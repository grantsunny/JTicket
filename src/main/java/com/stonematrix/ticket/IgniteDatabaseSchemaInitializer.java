package com.stonematrix.ticket;

import org.apache.ignite.Ignite;
import org.apache.ignite.Ignition;
import org.apache.ignite.configuration.IgniteConfiguration;
import org.apache.ignite.configuration.SqlConfiguration;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.support.DatabaseStartupValidator;

import javax.sql.DataSource;
import java.util.Arrays;


@Profile("production")
@Configuration
public class IgniteDatabaseSchemaInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    private Ignite startIgnite()  {
        IgniteConfiguration cfg = new IgniteConfiguration();
        SqlConfiguration sqlCfg = new SqlConfiguration();
        sqlCfg.setSqlSchemas("TKT");
        cfg.setSqlConfiguration(sqlCfg);

        return Ignition.start(cfg);
    }

    @Bean
    public DatabaseStartupValidator initDatabaseStartupValidator(DataSource dataSource) {
        DatabaseStartupValidator dsv = new DatabaseStartupValidator();
        dsv.setDataSource(dataSource);
        dsv.setInterval(5);  // Check every 5 seconds
        dsv.setTimeout(60);  // Max wait time of 60 seconds
        return dsv;
    }

    @Override
    public void initialize(ConfigurableApplicationContext context) {
        if (!Arrays.asList(context.getEnvironment().getActiveProfiles()).contains("production"))
            return;
        else {
            System.out.println("OpenTicketing: Starting Ignite Data Grid");
            Ignite ignite = startIgnite();
        }
    }
}
