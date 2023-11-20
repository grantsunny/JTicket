package com.stonematrix.ticket;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Service;

@Service
public class DerbyDatabaseSchemaInitializer implements ApplicationRunner {
    @Override
    public void run(ApplicationArguments args) throws Exception {
        System.out.println("StoneTicket: Verifying Derby DatabaseSchema... ");







    }
}
