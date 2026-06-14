package com.jticket.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySourcesPropertyResolver;
import org.springframework.core.io.ClassPathResource;

class Auth0ConfigurationTest {

    @Test
    void mapsShortEnvironmentVariablesToOAuth2Configuration() throws IOException {
        MutablePropertySources sources = new MutablePropertySources();
        sources.addFirst(new MapPropertySource("environment", Map.of(
                "JTICKET_AUTH0_ISSUER", "https://test-tenant.auth0.com/",
                "JTICKET_AUTH0_CLIENT_ID", "test-client",
                "JTICKET_AUTH0_CLIENT_SECRET", "test-secret",
                "JTICKET_AUTH0_AUDIENCE", "https://api.jticket.test")));

        new YamlPropertySourceLoader()
                .load("production", new ClassPathResource("application-production.yaml"))
                .forEach(sources::addLast);

        PropertySourcesPropertyResolver resolver =
                new PropertySourcesPropertyResolver(sources);

        assertThat(resolver.getProperty(
                "spring.security.oauth2.client.provider.auth0.issuer-uri"))
                .isEqualTo("https://test-tenant.auth0.com/");
        assertThat(resolver.getProperty(
                "spring.security.oauth2.resourceserver.jwt.issuer-uri"))
                .isEqualTo("https://test-tenant.auth0.com/");
        assertThat(resolver.getProperty(
                "spring.security.oauth2.client.registration.jticket.client-id"))
                .isEqualTo("test-client");
        assertThat(resolver.getProperty(
                "spring.security.oauth2.client.registration.jticket.client-secret"))
                .isEqualTo("test-secret");
        assertThat(resolver.getProperty(
                "spring.security.oauth2.resourceserver.jwt.audiences"))
                .isEqualTo("https://api.jticket.test");
        assertThat(resolver.getProperty("ticket.oauth2.audience"))
                .isEqualTo("https://api.jticket.test");
    }
}
