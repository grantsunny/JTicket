package com.jticket.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@WebMvcTest(properties = {
        "spring.profiles.active=production",
        "ticket.oauth2.audience=jticket-test"
})
@ContextConfiguration(classes = {
        OAuth2SecurityConfig.class,
        OAuth2SecurityConfigTest.TestSecurityBeans.class,
        OAuth2SecurityConfigTest.TestEndpoints.class
})
class OAuth2SecurityConfigTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void redirectsUnauthenticatedBrowserRequestToOidcLogin() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isFound())
                .andExpect(header().string(
                        "Location",
                        "http://localhost/oauth2/authorization/jticket"));
    }

    @Test
    void includesApiAudienceInAuthorizationRequest() throws Exception {
        MvcResult result = mockMvc.perform(get("/oauth2/authorization/jticket"))
                .andExpect(status().isFound())
                .andReturn();

        URI authorizationUri = URI.create(result.getResponse().getRedirectedUrl());
        assertThat(UriComponentsBuilder.fromUri(authorizationUri)
                .build()
                .getQueryParams()
                .getFirst("audience"))
                .isEqualTo("jticket-test");
    }

    @TestConfiguration
    static class TestSecurityBeans {

        @Bean
        ClientRegistrationRepository clientRegistrationRepository() {
            ClientRegistration registration = ClientRegistration
                    .withRegistrationId("jticket")
                    .clientId("test-client")
                    .clientSecret("test-secret")
                    .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                    .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                    .scope("openid", "profile", "email")
                    .authorizationUri("https://issuer.example/authorize")
                    .tokenUri("https://issuer.example/oauth/token")
                    .jwkSetUri("https://issuer.example/.well-known/jwks.json")
                    .userInfoUri("https://issuer.example/userinfo")
                    .userNameAttributeName("sub")
                    .clientName("JTicket test")
                    .build();
            return new InMemoryClientRegistrationRepository(registration);
        }

        @Bean
        JwtDecoder jwtDecoder() {
            return token -> {
                throw new UnsupportedOperationException("JWT decoding is not used by MockMvc post-processors");
            };
        }
    }

    @Test
    void rejectsUnauthenticatedApiRequestWithoutRedirecting() throws Exception {
        mockMvc.perform(get("/api/test"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void acceptsOidcSessionForBrowserAndApiRequests() throws Exception {
        MockHttpSession session = new MockHttpSession();

        mockMvc.perform(get("/").session(session).with(oauth2Login()))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/test").session(session))
                .andExpect(status().isOk());
    }

    @Test
    void acceptsJwtBearerAuthenticationForApiRequests() throws Exception {
        mockMvc.perform(get("/api/test").with(jwt()))
                .andExpect(status().isOk());
    }

    @Test
    void requiresReadScopeForEventReadApiRequests() throws Exception {
        mockMvc.perform(get("/api/events").with(jwt()
                        .authorities(new SimpleGrantedAuthority("SCOPE_order:write"))))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/events").with(jwt()
                        .authorities(new SimpleGrantedAuthority("SCOPE_event:read"))))
                .andExpect(status().isOk());
    }

    @Test
    void requiresWriteScopeForEventMutationApiRequests() throws Exception {
        mockMvc.perform(post("/api/events").with(jwt()
                        .authorities(new SimpleGrantedAuthority("SCOPE_event:read"))))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/events").with(jwt()
                        .authorities(new SimpleGrantedAuthority("SCOPE_event:write"))))
                .andExpect(status().isOk());

        mockMvc.perform(patch("/api/events/11111111-1111-1111-1111-111111111111/pricing").with(jwt()
                        .authorities(new SimpleGrantedAuthority("SCOPE_event:read"))))
                .andExpect(status().isForbidden());

        mockMvc.perform(patch("/api/events/11111111-1111-1111-1111-111111111111/pricing").with(jwt()
                        .authorities(new SimpleGrantedAuthority("SCOPE_event:write"))))
                .andExpect(status().isOk());
    }

    @Test
    void eventWriteDoesNotImplyEventRead() throws Exception {
        mockMvc.perform(get("/api/events").with(jwt()
                        .authorities(new SimpleGrantedAuthority("SCOPE_event:write"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void appliesFineGrainedAuthorizationToOidcBrowserSessions() throws Exception {
        mockMvc.perform(get("/api/events").with(oauth2Login()
                        .authorities(new SimpleGrantedAuthority("SCOPE_order:write"))))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/events").with(oauth2Login()
                        .authorities(new SimpleGrantedAuthority("SCOPE_event:read"))))
                .andExpect(status().isOk());
    }

    @Test
    void operatorScopesCanFollowProvisionDiagram() throws Exception {
        RequestPostProcessor operator = jwtWithScopes(
                "template:write",
                "event:read",
                "event:write",
                "venue:read",
                "seat:read");

        mockMvc.perform(get("/api/template").with(operator)).andExpect(status().isOk());
        mockMvc.perform(post("/api/template").with(operator)).andExpect(status().isOk());
        mockMvc.perform(get("/api/venues").with(operator)).andExpect(status().isOk());
        mockMvc.perform(get("/api/venues/11111111-1111-1111-1111-111111111111").with(operator))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/venues/11111111-1111-1111-1111-111111111111/svg").with(operator))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/venues/11111111-1111-1111-1111-111111111111/areas").with(operator))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/venues/11111111-1111-1111-1111-111111111111/areas/22222222-2222-2222-2222-222222222222").with(operator))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/venues/11111111-1111-1111-1111-111111111111/areas/22222222-2222-2222-2222-222222222222/seats").with(operator))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/venues/11111111-1111-1111-1111-111111111111/seats").with(operator))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/venues/11111111-1111-1111-1111-111111111111/seats/33333333-3333-3333-3333-333333333333").with(operator))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/events").with(operator)).andExpect(status().isOk());
        mockMvc.perform(get("/api/events/44444444-4444-4444-4444-444444444444").with(operator))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/events/44444444-4444-4444-4444-444444444444/prices").with(operator))
                .andExpect(status().isOk());
        mockMvc.perform(patch("/api/events/44444444-4444-4444-4444-444444444444/pricing").with(operator))
                .andExpect(status().isOk());
        mockMvc.perform(patch("/api/events/44444444-4444-4444-4444-444444444444/areas/22222222-2222-2222-2222-222222222222/pricing").with(operator))
                .andExpect(status().isOk());
        mockMvc.perform(patch("/api/events/44444444-4444-4444-4444-444444444444/seats/33333333-3333-3333-3333-333333333333/pricing").with(operator))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/events/44444444-4444-4444-4444-444444444444/sessions").with(operator))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/events/44444444-4444-4444-4444-444444444444/sessions/55555555-5555-5555-5555-555555555555").with(operator))
                .andExpect(status().isOk());
    }

    @Test
    void customerAndPaymentAgentScopesCanFollowPurchaseDiagram() throws Exception {
        RequestPostProcessor customer = jwtWithScopes("event:read", "order:write");
        RequestPostProcessor paymentAgent = jwtWithScopes("order:write");

        mockMvc.perform(get("/api/events").with(customer)).andExpect(status().isOk());
        mockMvc.perform(get("/api/events/44444444-4444-4444-4444-444444444444").with(customer))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/events/44444444-4444-4444-4444-444444444444/sessions").with(customer))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/events/44444444-4444-4444-4444-444444444444/venue").with(customer))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/events/44444444-4444-4444-4444-444444444444/venue/svg").with(customer))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/events/44444444-4444-4444-4444-444444444444/areas").with(customer))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/events/44444444-4444-4444-4444-444444444444/areas/22222222-2222-2222-2222-222222222222").with(customer))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/events/44444444-4444-4444-4444-444444444444/areas/22222222-2222-2222-2222-222222222222/seats").with(customer))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/events/44444444-4444-4444-4444-444444444444/seats/33333333-3333-3333-3333-333333333333").with(customer))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/events/44444444-4444-4444-4444-444444444444/seats/33333333-3333-3333-3333-333333333333/pricing").with(customer))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/orders").with(customer)).andExpect(status().isOk());
        mockMvc.perform(post("/api/orders").with(customer)).andExpect(status().isOk());
        mockMvc.perform(get("/api/orders/66666666-6666-6666-6666-666666666666").with(customer))
                .andExpect(status().isOk());
        mockMvc.perform(patch("/api/orders/66666666-6666-6666-6666-666666666666").with(paymentAgent))
                .andExpect(status().isOk());
    }

    @Test
    void customerAndAttendantScopesCanFollowCheckinDiagram() throws Exception {
        RequestPostProcessor customer = jwtWithScopes("order:write");
        RequestPostProcessor attendant = jwtWithScopes("event:read", "event:write");

        mockMvc.perform(get("/api/orders").with(customer)).andExpect(status().isOk());
        mockMvc.perform(get("/api/orders/66666666-6666-6666-6666-666666666666").with(customer))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/orders/66666666-6666-6666-6666-666666666666/token").with(customer))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/events/44444444-4444-4444-4444-444444444444").with(attendant))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/events/44444444-4444-4444-4444-444444444444/sessions").with(attendant))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/events/44444444-4444-4444-4444-444444444444/sessions/55555555-5555-5555-5555-555555555555").with(attendant))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/events/44444444-4444-4444-4444-444444444444/sessions/55555555-5555-5555-5555-555555555555").with(attendant))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/orders/66666666-6666-6666-6666-666666666666").with(customer))
                .andExpect(status().isOk());
    }

    @Test
    void mapsStandardScopesAndAuth0PermissionsToAuthorities() {
        Jwt token = new Jwt(
                "token",
                Instant.now(),
                Instant.now().plusSeconds(60),
                Map.of("alg", "none"),
                Map.of(
                        "sub", "test-user",
                        "scope", "event:read",
                        "permissions", List.of("order:write")));

        JwtAuthenticationConverter converter =
                new OAuth2SecurityConfig().jwtAuthenticationConverter();

        assertThat(converter.convert(token).getAuthorities())
                .extracting("authority")
                .containsExactlyInAnyOrder("SCOPE_event:read", "SCOPE_order:write");
    }

    private static RequestPostProcessor jwtWithScopes(String... scopes) {
        return jwt().authorities(List.of(scopes).stream()
                .map(scope -> new SimpleGrantedAuthority("SCOPE_" + scope))
                .collect(Collectors.toList()));
    }

    @RestController
    static class TestEndpoints {

        @GetMapping("/")
        String index() {
            return "index";
        }

        @GetMapping("/api/test")
        String api() {
            return "api";
        }

        @GetMapping("/api/events")
        String events() {
            return "events";
        }

        @PostMapping("/api/events")
        String createEvent() {
            return "event";
        }

        @GetMapping({
                "/api/template",
                "/api/venues",
                "/api/venues/{venueId}",
                "/api/venues/{venueId}/svg",
                "/api/venues/{venueId}/areas",
                "/api/venues/{venueId}/areas/{areaId}",
                "/api/venues/{venueId}/areas/{areaId}/seats",
                "/api/venues/{venueId}/seats",
                "/api/venues/{venueId}/seats/{seatId}",
                "/api/events/{eventId}",
                "/api/events/{eventId}/sessions",
                "/api/events/{eventId}/sessions/{sessionId}",
                "/api/events/{eventId}/venue",
                "/api/events/{eventId}/venue/svg",
                "/api/events/{eventId}/areas",
                "/api/events/{eventId}/areas/{areaId}",
                "/api/events/{eventId}/areas/{areaId}/seats",
                "/api/events/{eventId}/seats/{seatId}",
                "/api/events/{eventId}/seats/{seatId}/pricing",
                "/api/orders",
                "/api/orders/{orderId}"
        })
        String getFlowEndpoint() {
            return "flow";
        }

        @PostMapping({
                "/api/template",
                "/api/events/{eventId}/prices",
                "/api/events/{eventId}/sessions",
                "/api/events/{eventId}/sessions/{sessionId}",
                "/api/orders",
                "/api/orders/{orderId}/token"
        })
        String postFlowEndpoint() {
            return "flow";
        }

        @PatchMapping({
                "/api/events/{eventId}/pricing",
                "/api/events/{eventId}/areas/{areaId}/pricing",
                "/api/events/{eventId}/seats/{seatId}/pricing",
                "/api/orders/{orderId}"
        })
        String patchFlowEndpoint() {
            return "flow";
        }

        @DeleteMapping("/api/orders/{orderId}")
        String deleteFlowEndpoint() {
            return "flow";
        }
    }
}
