package com.jticket.endpoints;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.swagger.v3.oas.integration.GenericOpenApiContext;
import io.swagger.v3.oas.integration.OpenApiContextLocator;
import io.swagger.v3.oas.integration.api.OpenApiContext;
import io.swagger.v3.oas.models.OpenAPI;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/swagger")
public class ApiDocResource {

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response getOpenApiSpecJson() throws JsonProcessingException {
        GenericOpenApiContext<?> context =
                (GenericOpenApiContext<?>) OpenApiContextLocator.getInstance()
                        .getOpenApiContext(OpenApiContext.OPENAPI_CONTEXT_ID_DEFAULT);

        if (context != null) {
            OpenAPI openAPI = context.read();
            return Response.ok(context.getOutputJsonMapper().writeValueAsString(openAPI))
                    .type(MediaType.APPLICATION_JSON)
                    .build();
        } else
            return Response.status(Response.Status.NOT_ACCEPTABLE).build();
    }

    @GET
    @Produces("application/yaml")
    public Response getOpenApiSpecYaml() throws JsonProcessingException {
        GenericOpenApiContext<?> context =
                (GenericOpenApiContext<?>) OpenApiContextLocator.getInstance()
                        .getOpenApiContext(OpenApiContext.OPENAPI_CONTEXT_ID_DEFAULT);

        if (context != null) {
            OpenAPI openAPI = context.read();
            return Response.ok(context.getOutputYamlMapper().writeValueAsString(openAPI))
                    .type("application/yaml")
                    .build();
        } else
            return Response.status(Response.Status.NOT_ACCEPTABLE).build();
    }
}
