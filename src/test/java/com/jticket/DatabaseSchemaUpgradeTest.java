package com.jticket;

import java.util.UUID;

import org.junit.jupiter.api.Test;

class DatabaseSchemaUpgradeTest {

    @Test
    void existingDerbyDatabaseReceivesUpdatedViewsWithoutLosingData() {
        database().verifyUpgrade();
    }

    @Test
    void suppressedViewCreationErrorStopsStartup() {
        database().verifyBrokenViewStopsStartup();
    }

    @Test
    void incompatibleViewColumnsStopStartup() {
        database().verifyIncompatibleViewStopsStartup();
    }

    @Test
    void missingViewStopsStartupWhenSchemaInitializationIsDisabled() {
        database().verifyMissingViewWithInitializationDisabledStopsStartup();
    }

    private DatabaseSchemaUpgradeSupport database() {
        return new DatabaseSchemaUpgradeSupport(
                "jdbc:derby:memory:schema-upgrade-" + UUID.randomUUID() + ";create=true",
                "org.apache.derby.iapi.jdbc.AutoloadedDriver", "TKT", "derby.sql", ";");
    }
}
