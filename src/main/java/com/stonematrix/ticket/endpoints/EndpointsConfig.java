package com.stonematrix.ticket.endpoints;

import org.glassfish.jersey.server.ResourceConfig;
import org.springframework.stereotype.Component;

@Component
public class EndpointsConfig extends ResourceConfig {
    public EndpointsConfig() {
        registerClasses(
                DateObjectMapperProvider.class,
                TemplateResource.class,
                EventsApiResource.class,
                VenuesApiResource.class,
                SeatsApiResource.class,
                OrdersApiResource.class);
    }
}
