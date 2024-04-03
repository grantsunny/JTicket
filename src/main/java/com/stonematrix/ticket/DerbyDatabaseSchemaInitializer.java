package com.stonematrix.ticket;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Profile("dev")
@Service
public class DerbyDatabaseSchemaInitializer implements ApplicationRunner {
    @Override
    public void run(ApplicationArguments args)  {
        System.out.println("OpenTicket: Verifying Derby DatabaseSchema... ");
    }
}
