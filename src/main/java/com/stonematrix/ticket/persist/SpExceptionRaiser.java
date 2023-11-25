package com.stonematrix.ticket.persist;

import java.sql.SQLException;

/**
 * Workaround for Derby to raise exception from trigger
 * @see database.sql
 */
public class SpExceptionRaiser {
    public static void error(String error) throws SQLException {
        throw new SQLException(error);
    }
}
