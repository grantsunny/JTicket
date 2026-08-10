package com.jticket;

import static com.jticket.security.OAuth2Scopes.EVENT_READ;
import static com.jticket.security.OAuth2Scopes.EVENT_WRITE;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.concurrent.Executors;

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
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.Container.ExecResult;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.profiles.active=production,test")
@EnabledIfSystemProperty(named = "jticket.production-db-test", matches = "true")
class ProductionDatabaseIntegrationTest {

    private static final String AUDIENCE = "jticket-test-api";
    private static final int COCKROACH_SQL_PORT = 26258;
    private static LocalJwksProvider jwksProvider;
    private static GenericContainer<?> cockroach;

    private final HttpClient client = HttpClient.newHttpClient();
    private final int port;

    ProductionDatabaseIntegrationTest(@LocalServerPort int port) {
        this.port = port;
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", ProductionDatabaseIntegrationTest::jdbcUrl);
        properties.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        properties.add("spring.datasource.username", () -> "root");
        properties.add("spring.datasource.password", () -> "");
        properties.add("spring.security.oauth2.resourceserver.jwt.issuer-uri", () -> jwksProvider().baseUrl());
        properties.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri", () -> jwksProvider().jwkSetUri());
        properties.add("spring.security.oauth2.resourceserver.jwt.audiences", () -> AUDIENCE);
        properties.add("spring.security.oauth2.client.registration.jticket.client-id", () -> "jticket-test-client");
        properties.add("spring.security.oauth2.client.registration.jticket.client-secret", () -> "jticket-test-secret");
        properties.add("spring.security.oauth2.client.registration.jticket.authorization-grant-type",
                () -> "authorization_code");
        properties.add("spring.security.oauth2.client.registration.jticket.redirect-uri",
                () -> "{baseUrl}/login/oauth2/code/{registrationId}");
        properties.add("spring.security.oauth2.client.registration.jticket.scope", () -> "openid,profile,email");
        properties.add("spring.security.oauth2.client.registration.jticket.provider", () -> "local-oidc");
        properties.add("spring.security.oauth2.client.provider.local-oidc.authorization-uri",
                () -> jwksProvider().baseUrl() + "/authorize");
        properties.add("spring.security.oauth2.client.provider.local-oidc.token-uri",
                () -> jwksProvider().baseUrl() + "/oauth/token");
        properties.add("spring.security.oauth2.client.provider.local-oidc.jwk-set-uri",
                () -> jwksProvider().jwkSetUri());
        properties.add("spring.security.oauth2.client.provider.local-oidc.user-name-attribute", () -> "sub");
        properties.add("ticket.oauth2.audience", () -> AUDIENCE);
    }

    @AfterAll
    static void stopDependencies() {
        if (cockroach != null) {
            cockroach.stop();
        }
        if (jwksProvider != null) {
            jwksProvider.stop();
        }
    }

    @Test
    void productionProfileUsesCockroachAndEnforcesApiAuthentication() throws Exception {
        assertThat(send("GET", "/api/events", null, null).statusCode()).isEqualTo(401);

        String token = jwksProvider().accessToken(
                "operator",
                AUDIENCE,
                EVENT_READ + " " + EVENT_WRITE,
                List.of(EVENT_READ, EVENT_WRITE));

        HttpResponse<String> events = send("GET", "/api/events", token, null);
        assertThat(events.statusCode()).isEqualTo(200);
        assertThat(events.body()).isEqualTo("[]");

        String eventId = create(
                "/api/events",
                token,
                "{\"name\":\"Production Profile Event\",\"metadata\":{\"source\":\"production-test\"}}");
        assertThat(send("GET", "/api/events/" + eventId, token, null).body())
                .contains("Production Profile Event");

        String sessionId = create(
                "/api/events/" + eventId + "/sessions",
                token,
                "{\"name\":\"Production Profile Session\","
                        + "\"eventId\":\"" + eventId + "\","
                        + "\"startTime\":\"2026-07-01 10:00:00\","
                        + "\"endTime\":\"2026-07-01 12:00:00\"}");
        assertThat(send("GET", "/api/events/" + eventId + "/sessions", token, null).body())
                .contains(sessionId);

        String priceId = create(
                "/api/events/" + eventId + "/prices",
                token,
                "{\"name\":\"Production General Admission\","
                        + "\"eventId\":\"" + eventId + "\","
                        + "\"price\":" + BigDecimal.valueOf(42) + "}");
        assertThat(send("GET", "/api/events/" + eventId + "/prices/" + priceId, token, null).body())
                .contains("Production General Admission");

        assertThat(send("DELETE", "/api/events/" + eventId + "/sessions/" + sessionId, token, null).statusCode())
                .isEqualTo(204);
        assertThat(send("DELETE", "/api/events/" + eventId + "/prices/" + priceId, token, null).statusCode())
                .isEqualTo(204);
        assertThat(send("DELETE", "/api/events/" + eventId, token, null).statusCode()).isEqualTo(204);
        assertThat(send("GET", "/api/events/" + eventId, token, null).statusCode()).isEqualTo(404);
        assertPaidOrderTriggerIsActive();
        assertSessionOverlapTriggerIsActive();
    }

    private static void assertPaidOrderTriggerIsActive() throws Exception {
        ExecResult result = cockroach().execInContainer(
                "cockroach",
                "sql",
                "--insecure",
                "--host=localhost:" + COCKROACH_SQL_PORT,
                "--database=tkt",
                "--execute="
                        + "INSERT INTO TKT.Events (id, name, metadata) "
                        + "VALUES ('00000000-0000-0000-0000-000000000001', 'Trigger Event', '{}');"
                        + "INSERT INTO TKT.Sessions (id, name, eventId, startTime, endTime, metadata) "
                        + "VALUES ('00000000-0000-0000-0000-000000000002', 'Trigger Session', "
                        + "'00000000-0000-0000-0000-000000000001', "
                        + "'2026-07-01 14:00:00', '2026-07-01 16:00:00', '{}');"
                        + "INSERT INTO TKT.Orders (id, eventId, sessionId, userId, paymentAmount, metadata) "
                        + "VALUES ('00000000-0000-0000-0000-000000000003', "
                        + "'00000000-0000-0000-0000-000000000001', "
                        + "'00000000-0000-0000-0000-000000000002', 'trigger-user', 1, '{}');"
                        + "DELETE FROM TKT.Orders WHERE id = '00000000-0000-0000-0000-000000000003';");

        assertThat(result.getExitCode()).isNotZero();
        assertThat(result.getStderr()).contains("Paid order cannot be deleted");
    }

    private static void assertSessionOverlapTriggerIsActive() throws Exception {
        ExecResult result = cockroach().execInContainer(
                "cockroach",
                "sql",
                "--insecure",
                "--host=localhost:" + COCKROACH_SQL_PORT,
                "--database=tkt",
                "--execute="
                        + "INSERT INTO TKT.Venues (id, name, metadata) "
                        + "VALUES ('00000000-0000-0000-0000-000000000011', 'Trigger Venue', '{}');"
                        + "INSERT INTO TKT.Events (id, name, venueId, metadata) "
                        + "VALUES ('00000000-0000-0000-0000-000000000012', 'Overlap Event', "
                        + "'00000000-0000-0000-0000-000000000011', '{}');"
                        + "INSERT INTO TKT.Sessions (id, name, eventId, startTime, endTime, metadata) "
                        + "VALUES ('00000000-0000-0000-0000-000000000013', 'First Session', "
                        + "'00000000-0000-0000-0000-000000000012', "
                        + "'2026-07-02 10:00:00', '2026-07-02 12:00:00', '{}');"
                        + "INSERT INTO TKT.Sessions (id, name, eventId, startTime, endTime, metadata) "
                        + "VALUES ('00000000-0000-0000-0000-000000000014', 'Overlapping Session', "
                        + "'00000000-0000-0000-0000-000000000012', "
                        + "'2026-07-02 11:00:00', '2026-07-02 13:00:00', '{}');");

        assertThat(result.getExitCode()).isNotZero();
        assertThat(result.getStderr()).contains("Session time overlapping encountered within a given event");
    }

    private static GenericContainer<?> cockroach() {
        if (cockroach != null) {
            return cockroach;
        }
        GenericContainer<?> container = new GenericContainer<>(DockerImageName.parse("cockroachdb/cockroach:v26.2.4"))
                .withCommand(
                        "start-single-node",
                        "--insecure",
                        "--listen-addr=localhost:26257",
                        "--sql-addr=0.0.0.0:" + COCKROACH_SQL_PORT)
                .withExposedPorts(COCKROACH_SQL_PORT)
                .waitingFor(Wait.forListeningPort().withStartupTimeout(Duration.ofMinutes(2)));
        container.start();
        try {
            container.execInContainer(
                    "cockroach",
                    "sql",
                    "--insecure",
                    "--host=localhost:" + COCKROACH_SQL_PORT,
                    "--execute=CREATE DATABASE IF NOT EXISTS tkt;");
        } catch (Exception e) {
            container.stop();
            throw new IllegalStateException("Unable to initialize CockroachDB test database", e);
        }
        cockroach = container;
        return cockroach;
    }

    private static String jdbcUrl() {
        return "jdbc:postgresql://"
                + cockroach().getHost()
                + ":"
                + cockroach().getMappedPort(COCKROACH_SQL_PORT)
                + "/tkt?sslmode=disable";
    }

    private static LocalJwksProvider jwksProvider() {
        if (jwksProvider == null) {
            jwksProvider = LocalJwksProvider.start();
        }
        return jwksProvider;
    }

    private String create(String path, String token, String body) throws Exception {
        HttpResponse<String> response = send("POST", path, token, body);
        assertThat(response.statusCode()).isEqualTo(201);
        String location = response.headers().firstValue("Location").orElseThrow();
        return location.substring(location.lastIndexOf('/') + 1);
    }

    private HttpResponse<String> send(String method, String path, String token, String body)
            throws IOException, InterruptedException {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .method(
                        method,
                        body == null
                                ? HttpRequest.BodyPublishers.noBody()
                                : HttpRequest.BodyPublishers.ofString(body));
        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }
        if (body != null) {
            request.header("Content-Type", "application/json");
        }
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static final class LocalJwksProvider {

        private static final String KEY_ID = "jticket-production-test-key";

        private final HttpServer server;
        private final RSAPrivateKey privateKey;
        private final String jwkSet;

        private LocalJwksProvider(HttpServer server, KeyPair keyPair) {
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

        static LocalJwksProvider start() {
            try {
                KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
                generator.initialize(2048);
                KeyPair keyPair = generator.generateKeyPair();
                HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
                LocalJwksProvider provider = new LocalJwksProvider(server, keyPair);
                server.createContext("/.well-known/jwks.json", provider::jwkSetResponse);
                server.setExecutor(Executors.newCachedThreadPool());
                server.start();
                return provider;
            } catch (Exception e) {
                throw new IllegalStateException("Unable to start local JWKS provider", e);
            }
        }

        void stop() {
            server.stop(0);
        }

        String jwkSetUri() {
            return baseUrl() + "/.well-known/jwks.json";
        }

        String baseUrl() {
            return "http://127.0.0.1:" + server.getAddress().getPort();
        }

        String accessToken(String subject, String audience, String scope, List<String> permissions) {
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

        private void jwkSetResponse(HttpExchange exchange) throws IOException {
            byte[] body = jwkSet.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream output = exchange.getResponseBody()) {
                output.write(body);
            }
        }
    }
}
