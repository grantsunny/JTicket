package com.jticket.endpoints;

import io.swagger.v3.oas.integration.OpenApiConfigurationException;
import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.util.Set;

@Profile("dev")
@Configuration
public class ApiDocConfiguration {
    private static final String[] API_PACKAGES = {
            "com.jticket.endpoints",
            "com.jticket.api",
            "com.jticket.api.model"
    };

    @PostConstruct
    public void init() throws OpenApiConfigurationException {
        new io.swagger.v3.jaxrs2.integration.JaxrsOpenApiContextBuilder<>()
                .resourcePackages(Set.of(API_PACKAGES))
                .buildContext(true);
    }
}
