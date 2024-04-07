package com.jticket;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;


@SpringBootApplication(exclude = {
		SecurityAutoConfiguration.class
})

public class StoneTicketApplication {

	public static void main(String[] args) {

		SpringApplication app = new SpringApplication(StoneTicketApplication.class);
		app.addInitializers(new IgniteDatabaseSchemaInitializer());
		app.run(args);
	}
}

