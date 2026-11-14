package com.jticket;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import javax.sql.DataSource;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.sql.init.dependency.DependsOnDatabaseInitialization;
import org.springframework.stereotype.Component;

@Component
@DependsOnDatabaseInitialization
public final class DatabaseSchemaValidator implements InitializingBean {

    private final DataSource dataSource;

    public DatabaseSchemaValidator(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void afterPropertiesSet() throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            validateView(connection, "SessionStatistics",
                    "eventId, sessionId, totalSeats, orderedSeats, checkedInSeats, uncheckedInSeats");
            validateView(connection, "TicketingSeatStatus",
                    "eventId, sessionId, venueId, areaId, seatId, row, col, available, metadata, "
                            + "priceName, price, orderId, userId, checkedInTimestamp, status");
        }
    }

    private void validateView(Connection connection, String view, String columns) throws SQLException {
        String query = "SELECT " + columns + " FROM TKT." + view + " WHERE 1 = 0";
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery(query)) {
            result.getMetaData();
        } catch (SQLException failure) {
            throw new IllegalStateException("Required database view TKT." + view
                    + " is missing or incompatible after schema initialization. "
                    + "Apply the current database schema before starting JTicket.", failure);
        }
    }
}
