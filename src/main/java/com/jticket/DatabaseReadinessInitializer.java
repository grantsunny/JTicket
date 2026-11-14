package com.jticket;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.concurrent.TimeUnit;

import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;

public class DatabaseReadinessInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {

	@Override
	public void initialize(ConfigurableApplicationContext context) {

		System.out.print("JTicket: Verifying database for readiness ...");
		
		String dbUrl = context.getEnvironment().getProperty("spring.datasource.url");
		String dbUsername = context.getEnvironment().getProperty("spring.datasource.username");
		String dbPassword = context.getEnvironment().getProperty("spring.datasource.password");

		int retry = 0;
		int maxRetries = 30;
		
		for (; retry < maxRetries; retry++) {
			try (Connection connection = DriverManager.getConnection(dbUrl, dbUsername, dbPassword)) {
				if (!connection.isValid(5))
					throw new SQLException("Database connection is not valid");
				System.out.println(" done");
				return;
			} catch (SQLException ex) {
				System.out.print(".");
				try {
					TimeUnit.SECONDS.sleep(1);
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
					throw new RuntimeException("Interrupted while waiting for database readiness", e);
				}
			}
		}
		throw new RuntimeException("Database did not become ready within the allowed time.");
	}
}
