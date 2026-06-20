package com.jticket.endpoints;

import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.server.ServerProperties;
import org.glassfish.jersey.server.filter.RolesAllowedDynamicFeature;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

@Component
public class EndpointsConfig extends ResourceConfig {

    public EndpointsConfig(Environment environment) {
        property(ServerProperties.RESPONSE_SET_STATUS_OVER_SEND_ERROR, "true");
        if (environment.acceptsProfiles(Profiles.of("production"))) {
            register(RolesAllowedDynamicFeature.class);
        }
        registerClasses(
                DateObjectMapperProvider.class,
                ApiDocResource.class,
                AuthResource.class,
                TemplateResource.class,
                EventsApiResource.class,
                VenuesApiResource.class,
                SeatsApiResource.class,
                OrdersApiResource.class);
    }
}
