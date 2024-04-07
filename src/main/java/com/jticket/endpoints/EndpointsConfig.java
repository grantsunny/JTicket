package com.jticket.endpoints;

import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.server.ServerProperties;
import org.springframework.stereotype.Component;

@Component
public class EndpointsConfig extends ResourceConfig {

    public EndpointsConfig() {
        property(ServerProperties.RESPONSE_SET_STATUS_OVER_SEND_ERROR, "true");
        registerClasses(
                UserTokenFilter.class,
                DateObjectMapperProvider.class,
                AuthResource.class,
                TemplateResource.class,
                EventsApiResource.class,
                VenuesApiResource.class,
                SeatsApiResource.class,
                OrdersApiResource.class);
    }
}
