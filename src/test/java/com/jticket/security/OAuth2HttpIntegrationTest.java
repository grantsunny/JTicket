package com.jticket.security;

import static com.jticket.security.OAuth2Scopes.EVENT_READ;
import static com.jticket.security.OAuth2Scopes.EVENT_WRITE;
import static com.jticket.security.OAuth2Scopes.ORDER_WRITE;
import static com.jticket.security.OAuth2Scopes.SEAT_READ;
import static com.jticket.security.OAuth2Scopes.TEMPLATE_WRITE;
import static com.jticket.security.OAuth2Scopes.VENUE_READ;
import static org.assertj.core.api.Assertions.assertThat;

import java.net.CookieManager;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
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
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.util.UriComponentsBuilder;

import org.mybatis.spring.boot.autoconfigure.MybatisAutoConfiguration;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.server.filter.RolesAllowedDynamicFeature;

import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.SecurityContext;

@SpringBootTest(
        classes = OAuth2HttpIntegrationTest.TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.profiles.active=production")
class OAuth2HttpIntegrationTest {

    private static final LocalOidcProvider OIDC_PROVIDER = LocalOidcProvider.start();

    private final int port;

    @Autowired
    OAuth2HttpIntegrationTest(@LocalServerPort int port) {
        this.port = port;
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
        properties.add("spring.jersey.application-path", () -> "/api");
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

        HttpResponse<String> response = send("GET", "/api/principal", token);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).isEqualTo("api-user");
    }

    @Test
    void enforcesFineGrainedAuthorizationForRealSignedBearerToken() throws Exception {
        String token = OIDC_PROVIDER.accessToken(
                "api-user",
                "jticket-test-api",
                "order:write",
                List.of("order:write"));

        HttpResponse<String> response = send("GET", "/api/events", token);

        assertThat(response.statusCode()).isEqualTo(403);
    }

    @Test
    void doesNotTreatEventWriteAsEventReadForJaxRsResources() throws Exception {
        String token = OIDC_PROVIDER.accessToken(
                "attendant",
                "jticket-test-api",
                "event:write",
                List.of("event:write"));

        HttpResponse<String> readResponse = send("GET", "/api/events", token);

        assertThat(readResponse.statusCode()).isEqualTo(403);

        HttpResponse<String> writeResponse =
                send("PATCH", "/api/events/11111111-1111-1111-1111-111111111111/pricing", token);

        assertThat(writeResponse.statusCode()).isEqualTo(200);
        assertThat(writeResponse.body()).isEqualTo("event-write");
    }

    @Test
    void exchangesAuthorizationCodeAndReusesOidcSessionForApi() throws Exception {
        OIDC_PROVIDER.useAccessToken("browser-user", "event:read", List.of("event:read"));

        HttpClient client = HttpClient.newBuilder()
                .cookieHandler(new CookieManager())
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        login(client);

        HttpResponse<String> principal = client.send(
                request("GET", "/api/principal").build(),
                HttpResponse.BodyHandlers.ofString());

        assertThat(principal.statusCode()).isEqualTo(200);
        assertThat(principal.body()).isEqualTo("browser-user");

        HttpResponse<String> events = client.send(
                request("GET", "/api/events").build(),
                HttpResponse.BodyHandlers.ofString());

        assertThat(events.statusCode()).isEqualTo(200);
        assertThat(events.body()).isEqualTo("events");
    }

    @Test
    void oidcBrowserSessionCanUseBackendPortalAndOperatorApis() throws Exception {
        List<String> operatorScopes = List.of(
                TEMPLATE_WRITE,
                EVENT_READ,
                EVENT_WRITE,
                VENUE_READ,
                SEAT_READ);
        OIDC_PROVIDER.useAccessToken("operator-user", String.join(" ", operatorScopes), operatorScopes);

        HttpClient client = HttpClient.newBuilder()
                .cookieHandler(new CookieManager())
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();

        HttpResponse<String> unauthenticatedPortal = client.send(
                request("GET", "/event.html").build(),
                HttpResponse.BodyHandlers.ofString());

        assertThat(unauthenticatedPortal.statusCode()).isEqualTo(302);
        assertThat(URI.create(unauthenticatedPortal.headers().firstValue("Location").orElseThrow()).getPath())
                .isEqualTo("/oauth2/authorization/jticket");

        login(client);

        HttpResponse<String> portal = client.send(
                request("GET", "/event.html").build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(portal.statusCode()).isEqualTo(200);
        assertThat(portal.body())
                .contains("showCurrentUser(document.getElementById('user-info'))\n"
                        + "        .then(() => jticket.refreshFormEventVenueList());");

        HttpResponse<String> template = client.send(
                request("POST", "/api/template").build(),
                HttpResponse.BodyHandlers.ofString());
        HttpResponse<String> venues = client.send(
                request("GET", "/api/venues").build(),
                HttpResponse.BodyHandlers.ofString());
        HttpResponse<String> seats = client.send(
                request("GET", "/api/seats").build(),
                HttpResponse.BodyHandlers.ofString());
        HttpResponse<String> events = client.send(
                request("GET", "/api/events").build(),
                HttpResponse.BodyHandlers.ofString());
        HttpResponse<String> createEvent = client.send(
                request("POST", "/api/events").build(),
                HttpResponse.BodyHandlers.ofString());

        assertThat(template.statusCode()).isEqualTo(200);
        assertThat(venues.statusCode()).isEqualTo(200);
        assertThat(seats.statusCode()).isEqualTo(200);
        assertThat(events.statusCode()).isEqualTo(200);
        assertThat(createEvent.statusCode()).isEqualTo(200);
    }

    private void login(HttpClient client) throws IOException, InterruptedException {
        HttpResponse<String> authorization = client.send(
                request("GET", "/oauth2/authorization/jticket").build(),
                HttpResponse.BodyHandlers.ofString());

        assertThat(authorization.statusCode()).isEqualTo(302);

        URI redirect = URI.create(authorization.headers().firstValue("Location").orElseThrow());
        String state = URLDecoder.decode(UriComponentsBuilder.fromUri(redirect)
                .build()
                .getQueryParams()
                .getFirst("state"), StandardCharsets.UTF_8);
        String nonce = URLDecoder.decode(UriComponentsBuilder.fromUri(redirect)
                .build()
                .getQueryParams()
                .getFirst("nonce"), StandardCharsets.UTF_8);

        assertThat(redirect.toString()).startsWith(OIDC_PROVIDER.authorizationUri());
        assertThat(state).isNotBlank();
        assertThat(nonce).isNotBlank();

        OIDC_PROVIDER.useNonce(nonce);

        HttpResponse<String> login = client.send(
                request(
                        "GET",
                        "/login/oauth2/code/jticket?code=test-authorization-code&state="
                                + URLEncoder.encode(state, StandardCharsets.UTF_8))
                        .build(),
                HttpResponse.BodyHandlers.ofString());

        assertThat(login.statusCode()).isEqualTo(302);
        assertThat(URI.create(login.headers().firstValue("Location").orElseThrow()).getPath())
                .isIn("/", "/event.html");
    }

    private HttpResponse<String> send(String method, String path, String token)
            throws IOException, InterruptedException {
        return HttpClient.newHttpClient().send(
                request(method, path)
                        .header("Authorization", "Bearer " + token)
                        .build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private HttpRequest.Builder request(String method, String path) {
        return HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .method(method, HttpRequest.BodyPublishers.noBody());
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration(exclude = {
            DataSourceAutoConfiguration.class,
            DataSourceTransactionManagerAutoConfiguration.class,
            MybatisAutoConfiguration.class
    })
    @Import({OAuth2SecurityConfig.class, TestJerseyConfig.class})
    static class TestApplication {
    }

    @TestConfiguration
    static class TestJerseyConfig {

        @Bean
        ResourceConfig resourceConfig() {
            return new ResourceConfig()
                    .register(RolesAllowedDynamicFeature.class)
                    .register(TestJaxRsEndpoints.class);
        }
    }

    @Path("/")
    public static class TestJaxRsEndpoints {

        @GET
        @Path("principal")
        public String principal(@Context SecurityContext securityContext) {
            return securityContext.getUserPrincipal().getName();
        }

        @GET
        @Path("events")
        @RolesAllowed(EVENT_READ)
        public String events() {
            return "events";
        }

        @POST
        @Path("events")
        @RolesAllowed(EVENT_WRITE)
        public String createEvent() {
            return "event-write";
        }

        @PATCH
        @Path("events/{eventId}/pricing")
        @RolesAllowed(EVENT_WRITE)
        public String patchEventPricing() {
            return "event-write";
        }

        @GET
        @Path("orders")
        @RolesAllowed(ORDER_WRITE)
        public String orders() {
            return "orders";
        }

        @POST
        @Path("template")
        @RolesAllowed(TEMPLATE_WRITE)
        public String template() {
            return "template-write";
        }

        @GET
        @Path("venues")
        @RolesAllowed(VENUE_READ)
        public String venues() {
            return "venues";
        }

        @GET
        @Path("seats")
        @RolesAllowed(SEAT_READ)
        public String seats() {
            return "seats";
        }
    }

    private static final class LocalOidcProvider {

        private static final String KEY_ID = "jticket-test-key";

        private final HttpServer server;
        private final RSAPrivateKey privateKey;
        private final String jwkSet;
        private final AtomicReference<String> nonce = new AtomicReference<>();
        private final AtomicReference<TokenClaims> browserAccessToken =
                new AtomicReference<>(new TokenClaims(
                        "browser-user",
                        "event:read",
                        List.of("event:read")));

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

        void useAccessToken(String subject, String scope, List<String> permissions) {
            browserAccessToken.set(new TokenClaims(subject, scope, permissions));
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
            TokenClaims tokenClaims = browserAccessToken.get();
            String response = String.format(
                    "{\"access_token\":\"%s\","
                            + "\"id_token\":\"%s\","
                            + "\"token_type\":\"Bearer\","
                            + "\"expires_in\":300,"
                            + "\"scope\":\"openid profile email\"}",
                    accessToken(
                            tokenClaims.subject(),
                            "jticket-test-api",
                            tokenClaims.scope(),
                            tokenClaims.permissions()),
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

        private static final class TokenClaims {

            private final String subject;
            private final String scope;
            private final List<String> permissions;

            private TokenClaims(String subject, String scope, List<String> permissions) {
                this.subject = subject;
                this.scope = scope;
                this.permissions = permissions;
            }

            private String subject() {
                return subject;
            }

            private String scope() {
                return scope;
            }

            private List<String> permissions() {
                return permissions;
            }
        }
    }
}
