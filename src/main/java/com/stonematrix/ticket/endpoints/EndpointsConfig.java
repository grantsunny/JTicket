package com.stonematrix.ticket.endpoints;

import org.glassfish.jersey.server.ResourceConfig;
import org.springframework.stereotype.Component;

@Component
public class EndpointsConfig extends ResourceConfig {
    public EndpointsConfig() {
        registerClasses(
                TemplateResource.class,
                VenuesApiResource.class,
                SeatsApiResource.class,
                OrdersApiResource.class);

    }
}
