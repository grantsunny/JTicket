package com.jticket;

import java.sql.DriverManager;
import java.sql.SQLException;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;


@SpringBootApplication(exclude = {
		SecurityAutoConfiguration.class
})

public class JTicketApplication {

	public static void main(String[] args) throws SQLException {
		
		//FIXME: to suppress strange warning during startup: 
		//Registered driver with driverClassName=org.apache.derby.jdbc.EmbeddedDriver was not found, trying direct instantiation.
		DriverManager.registerDriver(new org.apache.derby.jdbc.EmbeddedDriver());
		
		SpringApplication app = new SpringApplication(JTicketApplication.class);
		app.addInitializers(new IgniteDatabaseSchemaInitializer());
		app.run(args);
	}
}

