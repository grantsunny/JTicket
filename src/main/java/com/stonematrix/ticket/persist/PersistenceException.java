package com.stonematrix.ticket.persist;

import jakarta.annotation.Nonnull;
import java.sql.SQLException;
import java.util.Iterator;

public class PersistenceException extends SQLException {


    private SQLException getSQLException() {
        Throwable cause = getCause();
        while (cause != null) {
            if (cause instanceof SQLException)
                return (SQLException) cause;

            cause = cause.getCause();
        }
        return null;
    }

    public PersistenceException(Throwable targetException) {
    }

    public PersistenceException(String reason) {
        super(reason);
    }

    public String getSQLState() {
        SQLException sqlException = getSQLException();
        if (sqlException != null)
            return sqlException.getSQLState();
        else
            return null;
    }

    /**
     * Retrieves the vendor-specific exception code
     * for this {@code SQLException} object.
     *
     * @return the vendor's error code
     */
    public int getErrorCode() {
        SQLException sqlException = getSQLException();
        if (sqlException != null)
            return sqlException.getErrorCode();
        else
            return -1;
    }

    /**
     * Retrieves the exception chained to this
     * {@code SQLException} object by setNextException(SQLException ex).
     *
     * @return the next {@code SQLException} object in the chain;
     *         {@code null} if there are none
     * @see #setNextException
     */
    public SQLException getNextException() {
        SQLException sqlException = getSQLException();
        if (sqlException != null)
            return sqlException.getNextException();
        else
            return null;
    }

    public void setNextException(SQLException ex) {
        SQLException sqlException = getSQLException();
        if (sqlException != null)
            sqlException.setNextException(ex);
    }

    @Nonnull
    public Iterator<Throwable> iterator() {
        SQLException sqlException = getSQLException();
        if (sqlException != null)
            return sqlException.iterator();
        else
            return super.iterator();
    }
}
