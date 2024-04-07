package com.jticket;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;


@SpringBootApplication(exclude = {
		SecurityAutoConfiguration.class
})

public class JTicketApplication {

	public static void main(String[] args) {

		SpringApplication app = new SpringApplication(JTicketApplication.class);
		app.addInitializers(new IgniteDatabaseSchemaInitializer());
		app.run(args);
	}
}

