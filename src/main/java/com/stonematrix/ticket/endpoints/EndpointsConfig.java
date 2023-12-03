package com.stonematrix.ticket.endpoints;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import jakarta.ws.rs.core.MediaType;
import org.glassfish.jersey.jackson.internal.jackson.jaxrs.json.JacksonJaxbJsonProvider;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.server.ServerProperties;
import org.springframework.stereotype.Component;

@Component
public class EndpointsConfig extends ResourceConfig {
    public EndpointsConfig() {

        property(ServerProperties.RESPONSE_SET_STATUS_OVER_SEND_ERROR, "true");
        registerClasses(
                DateObjectMapperProvider.class,
                TemplateResource.class,
                EventsApiResource.class,
                VenuesApiResource.class,
                SeatsApiResource.class,
                OrdersApiResource.class);
    }
}
