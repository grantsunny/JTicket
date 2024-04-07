package com.jticket.endpoints;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.Response;

import jakarta.ws.rs.ext.Provider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;


/*
 * Look at the Annotation below.
 *
 * This is strange behavior that it have to be declared as @Component to make @Value injection works.
 * More tricky thing is it won't be able to be injected via @Inject and @Autowire.
 * Anyway this is the only way to make it work in JAX-RS and access configuration of Spring boot.
 */
@Component
@Provider
public class UserTokenFilter implements ContainerRequestFilter {

    @Value("${ticket.user-token.path:/}")
    private String baseUrl;

    @Value("${ticket.user-token.required:false}")
    private boolean userTokenRequired;

    @Value("${ticket.user-token.name:x-ticket-userId}")
    private String userTokenName;

    @Override
    public void filter(ContainerRequestContext requestContext) throws IOException {
        if (userTokenRequired) {
            if (requestContext.getUriInfo().getRequestUri().getPath().startsWith(baseUrl)){
                if (!(requestContext.getCookies().containsKey(userTokenName))
                        && !(requestContext.getHeaders().containsKey(userTokenName)))
                    requestContext.abortWith(
                            Response.status(Response.Status.UNAUTHORIZED)
                                    .entity("Missing cookie " + userTokenName + " in request")
                                    .build());
            }
        }
    }
}
