package com.jticket.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;

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
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.bind.annotation.GetMapping;
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
    }
}
