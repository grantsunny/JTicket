package com.stonematrix.ticket.persist;


/**
 * Workaround for Derby to raise exception from trigger
 * refer to database.sql
 */
public class SpExceptionRaiser {
    public static void error(String error) throws PersistenceException {
        throw new PersistenceException(error);
    }
}
