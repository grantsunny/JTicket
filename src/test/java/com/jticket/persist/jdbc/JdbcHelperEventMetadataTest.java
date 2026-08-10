package com.jticket.persist.jdbc;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.Statement;
import java.util.Map;
import java.util.UUID;

import javax.sql.DataSource;

import com.jticket.api.model.Event;
import com.jticket.api.model.Venue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.util.ReflectionTestUtils;

class JdbcHelperEventMetadataTest {

    private JdbcHelper helper;
    private UUID venueId;

    @BeforeEach
    void setUp() throws Exception {
        DataSource dataSource = new DriverManagerDataSource(
                "jdbc:derby:memory:jdbc-helper-event-" + UUID.randomUUID() + ";create=true",
                "TKT",
                "");
        createSchema(dataSource);

        helper = new JdbcHelper();
        ReflectionTestUtils.setField(helper, "dataSource", dataSource);

        venueId = UUID.randomUUID();
        helper.saveVenue(new Venue()
                .id(venueId)
                .name("JDBC Event Venue")
                .metadata(Map.of("venue", "test")), "<svg/>");
    }

    @Test
    void saveAndUpdateEventPreserveMetadataOnJdbcProfile() throws Exception {
        UUID eventId = UUID.randomUUID();
        helper.saveEvent(new Event()
                .id(eventId)
                .venueId(venueId)
                .name("JDBC Event")
                .metadata(Map.of("campaign", "launch", "capacity", 120)));

        Event saved = helper.loadEvent(eventId);
        assertThat(saved.getMetadata())
                .containsEntry("campaign", "launch")
                .containsEntry("capacity", 120);

        helper.updateEvent(eventId, new Event()
                .venueId(venueId)
                .name("Updated JDBC Event")
                .metadata(Map.of("campaign", "encore", "vip", true)));

        Event updated = helper.loadEvent(eventId);
        assertThat(updated.getName()).isEqualTo("Updated JDBC Event");
        assertThat(updated.getMetadata())
                .containsEntry("campaign", "encore")
                .containsEntry("vip", true);
    }

    private void createSchema(DataSource dataSource) throws Exception {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE SCHEMA TKT");
            statement.execute("SET SCHEMA TKT");
            statement.execute("""
                    CREATE TABLE TKT.Venues (
                        id VARCHAR(36) PRIMARY KEY NOT NULL,
                        name VARCHAR(255) NOT NULL,
                        metadata CLOB,
                        svg CLOB,
                        UNIQUE (name)
                    )
                    """);
            statement.execute("""
                    CREATE TABLE TKT.Events (
                        id VARCHAR(36) PRIMARY KEY NOT NULL,
                        name VARCHAR(255) NOT NULL,
                        venueId VARCHAR(36),
                        metadata CLOB,
                        FOREIGN KEY (venueId) REFERENCES TKT.Venues(id),
                        UNIQUE(name)
                    )
                    """);
        }
    }
}
