package com.jticket.persist;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.sql.SQLException;

import org.junit.jupiter.api.Test;

class PersistenceExceptionTest {

    @Test
    void preservesWrappedSqlExceptionDetails() {
        SQLException cause = new SQLException("conflict", "23505", 42);

        PersistenceException exception = new PersistenceException(cause);

        assertSame(cause, exception.getCause());
        assertEquals("23505", exception.getSQLState());
        assertEquals(42, exception.getErrorCode());
    }
}
