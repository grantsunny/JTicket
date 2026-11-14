package com.jticket;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Locale;

import javax.sql.DataSource;

import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceInitializationAutoConfiguration;
import org.springframework.boot.test.context.assertj.AssertableApplicationContext;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

final class DatabaseSchemaUpgradeSupport {

    private final DataSource dataSource;
    private final String schema;
    private final String separator;
    private final ApplicationContextRunner contextRunner;

    DatabaseSchemaUpgradeSupport(String url, String driver, String username, String schema, String separator) {
        this.dataSource = new DriverManagerDataSource(url, username, "");
        this.schema = schema;
        this.separator = separator;
        this.contextRunner = new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        DataSourceAutoConfiguration.class, DataSourceInitializationAutoConfiguration.class))
                .withUserConfiguration(DatabaseSchemaValidator.class)
                .withPropertyValues(
                        "spring.datasource.url=" + url,
                        "spring.datasource.driver-class-name=" + driver,
                        "spring.datasource.username=" + username,
                        "spring.datasource.password=",
                        "spring.datasource.hikari.minimum-idle=1",
                        "spring.datasource.hikari.maximum-pool-size=2",
                        "spring.sql.init.mode=always",
                        "spring.sql.init.continue-on-error=true",
                        "spring.sql.init.schema-locations=classpath:" + schema,
                        "spring.sql.init.separator=" + separator);
    }

    void verifyUpgrade() {
        initializeExistingDatabase();
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        assertThat(viewDefinition(jdbc, "SessionStatistics")).contains("TKT.SKU");
        assertThat(viewDefinition(jdbc, "TicketingSeatStatus")).contains("TKT.ORDERSEATS");

        // Two schema-refreshing startups upgrade legacy views and remain idempotent.
        for (int startup = 0; startup < 2; startup++) {
            contextRunner.run(this::assertUpgradedDatabase);
        }

        // Serving replicas must validate existing views without executing even a broken schema script.
        contextRunner.withPropertyValues(
                "spring.sql.init.mode=never",
                "spring.sql.init.schema-locations=classpath:broken-read-view.sql")
                .run(this::assertUpgradedDatabase);
    }

    private void assertUpgradedDatabase(AssertableApplicationContext context) {
        assertThat(context).hasNotFailed().hasSingleBean(DatabaseSchemaValidator.class);
        JdbcTemplate restarted = new JdbcTemplate(context.getBean(DataSource.class));
        assertThat(restarted.queryForObject(
                "SELECT totalSeats FROM TKT.SessionStatistics WHERE sessionId='s1'", Integer.class))
                .isEqualTo(3);
        assertThat(restarted.queryForObject(
                "SELECT checkedInSeats FROM TKT.SessionStatistics WHERE sessionId='s1'", Integer.class))
                .isEqualTo(1);
        assertThat(restarted.queryForObject(
                "SELECT orderedSeats FROM TKT.SessionStatistics WHERE sessionId='s2'", Integer.class))
                .isZero();
        assertThat(restarted.queryForObject(
                "SELECT status FROM TKT.TicketingSeatStatus WHERE sessionId='s1' AND seatId='seat1'",
                String.class)).isEqualTo("checkedIn");
        assertThat(restarted.queryForObject(
                "SELECT status FROM TKT.TicketingSeatStatus WHERE sessionId='s2' AND seatId='seat1'",
                String.class)).isEqualTo("open");
        assertThat(restarted.queryForObject(
                "SELECT metadata FROM TKT.Orders WHERE id='o1'", String.class))
                .isEqualTo("{\"preserved\":true}");
        assertThat(restarted.queryForObject("SELECT COUNT(*) FROM TKT.OrderSeats", Integer.class))
                .isEqualTo(1);
        assertThat(viewDefinition(restarted, "SessionStatistics"))
                .contains("TKT.SEATS").doesNotContain("TKT.SKU");
        assertThat(viewDefinition(restarted, "TicketingSeatStatus"))
                .doesNotContain("TKT.ORDERSEATS");
    }

    void verifyBrokenViewStopsStartup() {
        initializeExistingDatabase();
        assertStartupFailure("broken-read-view.sql", "TKT.SessionStatistics");
    }

    void verifyIncompatibleViewStopsStartup() {
        initializeExistingDatabase();
        assertStartupFailure("incompatible-read-view.sql", "TKT.TicketingSeatStatus");
    }

    void verifyMissingViewWithInitializationDisabledStopsStartup() {
        initializeExistingDatabase();
        new JdbcTemplate(dataSource).execute("DROP VIEW TKT.SessionStatistics");
        assertStartupFailure(contextRunner.withPropertyValues("spring.sql.init.mode=never"),
                "TKT.SessionStatistics");
    }

    private void assertStartupFailure(String script, String view) {
        assertStartupFailure(contextRunner.withPropertyValues(
                "spring.sql.init.schema-locations=classpath:" + script,
                "spring.sql.init.separator=;;"), view);
    }

    private void assertStartupFailure(ApplicationContextRunner runner, String view) {
        runner.run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).hasStackTraceContaining(
                    "Required database view " + view + " is missing or incompatible");
        });
    }

    private void initializeExistingDatabase() {
        ResourceDatabasePopulator initialSchema = new ResourceDatabasePopulator(new ClassPathResource(schema));
        initialSchema.setSeparator(separator);
        initialSchema.setContinueOnError(true);
        initialSchema.execute(dataSource);
        ResourceDatabasePopulator legacyViews = new ResourceDatabasePopulator(
                new ClassPathResource("legacy-read-views.sql"));
        legacyViews.setSeparator(";;");
        legacyViews.execute(dataSource);
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        jdbc.update("INSERT INTO TKT.Venues(id,name) VALUES ('v1','Existing venue')");
        jdbc.update("INSERT INTO TKT.Areas(id,venueId,name) VALUES ('a1','v1','Main')");
        jdbc.update("INSERT INTO TKT.Events(id,venueId,name) VALUES ('e1','v1','Existing event')");
        jdbc.update("INSERT INTO TKT.Sessions(id,eventId,name,startTime,endTime) VALUES "
                + "('s1','e1','First','2031-01-01 10:00:00','2031-01-01 12:00:00'),"
                + "('s2','e1','Second','2031-01-02 10:00:00','2031-01-02 12:00:00')");
        jdbc.update("INSERT INTO TKT.Seats(id,areaId,row,col,available) VALUES "
                + "('seat1','a1',0,0,true),('seat2','a1',0,1,false),('seat3','a1',0,2,true)");
        jdbc.update("INSERT INTO TKT.Orders(id,eventId,sessionId,userId,metadata) "
                + "VALUES ('o1','e1','s1','customer','{\"preserved\":true}')");
        jdbc.update("INSERT INTO TKT.OrderSeats(orderId,eventId,sessionId,seatId,checkedInTimestamp) "
                + "VALUES ('o1','e1','s1','seat1','2031-01-01 10:01:00')");
    }

    private String viewDefinition(JdbcTemplate jdbc, String view) {
        String definition;
        if (schema.equals("derby.sql")) {
            definition = jdbc.queryForObject("SELECT v.VIEWDEFINITION FROM SYS.SYSVIEWS v "
                    + "JOIN SYS.SYSTABLES t ON v.TABLEID=t.TABLEID WHERE t.TABLENAME=?",
                    String.class, view.toUpperCase(Locale.ROOT));
        } else {
            definition = jdbc.queryForObject("SELECT view_definition FROM information_schema.views "
                    + "WHERE table_schema='tkt' AND table_name=?", String.class, view.toLowerCase(Locale.ROOT));
        }
        assertThat(definition).isNotNull();
        return definition.toUpperCase(Locale.ROOT).replace("\"", "");
    }
}
