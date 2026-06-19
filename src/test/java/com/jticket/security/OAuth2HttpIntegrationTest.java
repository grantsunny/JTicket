package com.jticket.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.Authentication;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import org.mybatis.spring.boot.autoconfigure.MybatisAutoConfiguration;

@SpringBootTest(
        classes = OAuth2HttpIntegrationTest.TestApplication.class,
        properties = "spring.profiles.active=production")
@AutoConfigureMockMvc
class OAuth2HttpIntegrationTest {

    private static final LocalOidcProvider OIDC_PROVIDER = LocalOidcProvider.start();

    private final MockMvc mockMvc;

    @Autowired
    OAuth2HttpIntegrationTest(MockMvc mockMvc) {
        this.mockMvc = mockMvc;
    }

    @DynamicPropertySource
    static void oauth2Properties(DynamicPropertyRegistry properties) {
        properties.add("spring.security.oauth2.client.registration.jticket.client-id",
                () -> "jticket-test-client");
        properties.add("spring.security.oauth2.client.registration.jticket.client-secret",
                () -> "jticket-test-secret");
        properties.add("spring.security.oauth2.client.registration.jticket.authorization-grant-type",
                () -> "authorization_code");
        properties.add("spring.security.oauth2.client.registration.jticket.redirect-uri",
                () -> "{baseUrl}/login/oauth2/code/{registrationId}");
        properties.add("spring.security.oauth2.client.registration.jticket.scope",
                () -> "openid,profile,email");
        properties.add("spring.security.oauth2.client.registration.jticket.provider",
                () -> "local-oidc");
        properties.add("spring.security.oauth2.client.provider.local-oidc.authorization-uri",
                OIDC_PROVIDER::authorizationUri);
        properties.add("spring.security.oauth2.client.provider.local-oidc.token-uri",
                OIDC_PROVIDER::tokenUri);
        properties.add("spring.security.oauth2.client.provider.local-oidc.jwk-set-uri",
                OIDC_PROVIDER::jwkSetUri);
        properties.add("spring.security.oauth2.client.provider.local-oidc.user-name-attribute",
                () -> "sub");
        properties.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri",
                OIDC_PROVIDER::jwkSetUri);
        properties.add("spring.security.oauth2.resourceserver.jwt.issuer-uri",
                OIDC_PROVIDER::baseUrl);
        properties.add("spring.security.oauth2.resourceserver.jwt.audiences",
                () -> "jticket-test-api");
        properties.add("ticket.oauth2.audience", () -> "jticket-test-api");
    }

    @AfterAll
    static void stopProvider() {
        OIDC_PROVIDER.stop();
    }

    @Test
    void acceptsRealSignedBearerToken() throws Exception {
        String token = OIDC_PROVIDER.accessToken(
                "api-user",
                "jticket-test-api",
                List.of("event:read", "order:write"));

        mockMvc.perform(get("/api/principal")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(content().string("api-user"));
    }

    @Test
    void enforcesFineGrainedAuthorizationForRealSignedBearerToken() throws Exception {
        String token = OIDC_PROVIDER.accessToken(
                "api-user",
                "jticket-test-api",
                "order:write",
                List.of("order:write"));

        mockMvc.perform(get("/api/events")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void exchangesAuthorizationCodeAndReusesOidcSessionForApi() throws Exception {
        MvcResult authorization = mockMvc.perform(get("/oauth2/authorization/jticket"))
                .andExpect(status().isFound())
                .andReturn();

        URI redirect = URI.create(authorization.getResponse().getRedirectedUrl());
        String state = URLDecoder.decode(UriComponentsBuilder.fromUri(redirect)
                .build()
                .getQueryParams()
                .getFirst("state"), StandardCharsets.UTF_8);
        String nonce = URLDecoder.decode(UriComponentsBuilder.fromUri(redirect)
                .build()
                .getQueryParams()
                .getFirst("nonce"), StandardCharsets.UTF_8);
        MockHttpSession session =
                (MockHttpSession) authorization.getRequest().getSession(false);

        assertThat(redirect.toString()).startsWith(OIDC_PROVIDER.authorizationUri());
        assertThat(state).isNotBlank();
        assertThat(nonce).isNotBlank();
        assertThat(session).isNotNull();

        OIDC_PROVIDER.useNonce(nonce);

        mockMvc.perform(get("/login/oauth2/code/jticket")
                        .session(session)
                        .queryParam("code", "test-authorization-code")
                        .queryParam("state", state))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/"));

        mockMvc.perform(get("/api/principal")
                        .session(session))
                .andExpect(status().isOk())
                .andExpect(content().string("browser-user"));

        mockMvc.perform(get("/api/events")
                        .session(session))
                .andExpect(status().isOk())
                .andExpect(content().string("events"));
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration(exclude = {
            DataSourceAutoConfiguration.class,
            DataSourceTransactionManagerAutoConfiguration.class,
            MybatisAutoConfiguration.class
    })
    @Import({OAuth2SecurityConfig.class, TestEndpoints.class})
    static class TestApplication {
    }

    @RestController
    static class TestEndpoints {

        @GetMapping("/api/principal")
        String principal(Authentication authentication) {
            return authentication.getName();
        }

        @GetMapping("/api/events")
        String events() {
            return "events";
        }
    }

    private static final class LocalOidcProvider {

        private static final String KEY_ID = "jticket-test-key";

        private final HttpServer server;
        private final RSAPrivateKey privateKey;
        private final String jwkSet;
        private final AtomicReference<String> nonce = new AtomicReference<>();

        private LocalOidcProvider(HttpServer server, KeyPair keyPair) {
            this.server = server;
            privateKey = (RSAPrivateKey) keyPair.getPrivate();
            jwkSet = "{\"keys\":["
                    + new RSAKey.Builder((RSAPublicKey) keyPair.getPublic())
                    .keyID(KEY_ID)
                    .algorithm(JWSAlgorithm.RS256)
                    .build()
                    .toPublicJWK()
                    + "]}";
        }

        static LocalOidcProvider start() {
            try {
                KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
                generator.initialize(2048);
                KeyPair keyPair = generator.generateKeyPair();

                HttpServer server = HttpServer.create(
                        new InetSocketAddress("127.0.0.1", 0),
                        0);
                LocalOidcProvider provider = new LocalOidcProvider(server, keyPair);
                server.createContext("/oauth/token", provider::tokenResponse);
                server.createContext("/.well-known/jwks.json", provider::jwkSetResponse);
                server.setExecutor(Executors.newCachedThreadPool());
                server.start();
                return provider;
            } catch (Exception e) {
                throw new IllegalStateException("Unable to start local OIDC provider", e);
            }
        }

        void stop() {
            server.stop(0);
        }

        String authorizationUri() {
            return baseUrl() + "/authorize";
        }

        String tokenUri() {
            return baseUrl() + "/oauth/token";
        }

        String jwkSetUri() {
            return baseUrl() + "/.well-known/jwks.json";
        }

        void useNonce(String value) {
            nonce.set(value);
        }

        String accessToken(String subject, String audience, List<String> permissions) {
            return accessToken(subject, audience, "event:read", permissions);
        }

        String accessToken(
                String subject,
                String audience,
                String scope,
                List<String> permissions) {
            return signedToken(new JWTClaimsSet.Builder()
                    .subject(subject)
                    .audience(audience)
                    .issuer(baseUrl())
                    .issueTime(Date.from(Instant.now()))
                    .expirationTime(Date.from(Instant.now().plusSeconds(300)))
                    .claim("scope", scope)
                    .claim("permissions", permissions)
                    .build());
        }

        private String idToken() {
            return signedToken(new JWTClaimsSet.Builder()
                    .subject("browser-user")
                    .audience("jticket-test-client")
                    .issuer(baseUrl())
                    .issueTime(Date.from(Instant.now()))
                    .expirationTime(Date.from(Instant.now().plusSeconds(300)))
                    .claim("nonce", nonce.get())
                    .build());
        }

        private String signedToken(JWTClaimsSet claims) {
            try {
                SignedJWT jwt = new SignedJWT(
                        new JWSHeader.Builder(JWSAlgorithm.RS256)
                                .keyID(KEY_ID)
                                .build(),
                        claims);
                jwt.sign(new RSASSASigner(privateKey));
                return jwt.serialize();
            } catch (Exception e) {
                throw new IllegalStateException("Unable to sign test token", e);
            }
        }

        private void tokenResponse(HttpExchange exchange) throws IOException {
            String response = String.format(
                    "{\"access_token\":\"%s\","
                            + "\"id_token\":\"%s\","
                            + "\"token_type\":\"Bearer\","
                            + "\"expires_in\":300,"
                            + "\"scope\":\"openid profile email\"}",
                    accessToken("browser-user", "jticket-test-api", List.of("event:read")),
                    idToken());
            sendJson(exchange, response);
        }

        private void jwkSetResponse(HttpExchange exchange) throws IOException {
            sendJson(exchange, jwkSet);
        }

        private void sendJson(HttpExchange exchange, String response) throws IOException {
            byte[] body = response.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream output = exchange.getResponseBody()) {
                output.write(body);
            }
        }

        private String baseUrl() {
            return "http://127.0.0.1:" + server.getAddress().getPort();
        }
    }
}
