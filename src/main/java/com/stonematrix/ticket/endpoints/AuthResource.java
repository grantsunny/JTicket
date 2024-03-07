package com.stonematrix.ticket.endpoints;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.SecurityContext;

@Path(("/auth"))
public class AuthResource {
    @Inject
    private SecurityContext securityContext;

    @GET
    @Path("/userinfo")
    @Produces(MediaType.TEXT_PLAIN)
    public String currentUser() {
        java.security.Principal userPrincipal = securityContext.getUserPrincipal();
        return userPrincipal != null ? userPrincipal.getName() : "anonymous";
    }
}
