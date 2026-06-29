package com.jticket.staticui;

import static org.assertj.core.api.Assertions.assertThat;

import com.jticket.security.DevelopmentSecurityConfig;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.web.resources.static-locations=classpath:/static/")
@ActiveProfiles("dev")
class StaticUiReachabilityTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void applicationRootLandsOnIndexUi() {
        ResponseEntity<String> response = getFollowingRedirect("/");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody())
                .contains("<title>Welcome to JTicket!</title>")
                .contains("welcome to JTicket!")
                .contains("href=\"/venue.html\"")
                .contains("href=\"/event.html\"")
                .contains("href=\"/order.html\"");
    }

    @Test
    void indexHtmlIsReachableFromRootPath() {
        ResponseEntity<String> response = restTemplate.getForEntity("/index.html", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody())
                .contains("<title>Welcome to JTicket!</title>")
                .contains("welcome to JTicket!")
                .contains("href=\"/venue.html\"")
                .contains("href=\"/event.html\"")
                .contains("href=\"/order.html\"");
    }

    @Test
    void venueHtmlIsReachableFromRootPath() {
        ResponseEntity<String> response = restTemplate.getForEntity("/venue.html", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody())
                .contains("<title>Welcome to JTicket!</title>")
                .contains("Upload venue template")
                .contains("id=\"venuePreview\"")
                .contains("id=\"seatsContainer\"");
    }

    @Test
    void orderHtmlIsReachableFromRootPath() {
        ResponseEntity<String> response = restTemplate.getForEntity("/order.html", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody())
                .contains("<title>Welcome to JTicket!</title>")
                .contains("Order Management")
                .contains("id=\"orderEventId\"")
                .contains("id=\"orderList\"");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "/script.js",
            "/style.css",
            "/modal-style.css",
            "/order.js"
    })
    void linkedStaticAssetsAreReachable(String path) {
        ResponseEntity<String> response = restTemplate.getForEntity(path, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotBlank();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "/static/index.html",
            "/static/venue.html",
            "/static/order.html",
            "/static/style.css"
    })
    void classpathStaticDirectoryNameIsNotPartOfDefaultUrlPath(String path) {
        ResponseEntity<String> response = restTemplate.getForEntity(path, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    private ResponseEntity<String> getFollowingRedirect(String path) {
        ResponseEntity<String> response = restTemplate.getForEntity(path, String.class);
        if (response.getStatusCode().is3xxRedirection()) {
            String location = response.getHeaders().getFirst(HttpHeaders.LOCATION);
            assertThat(location).isNotBlank();
            return restTemplate.getForEntity(location, String.class);
        }
        return response;
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @Import(DevelopmentSecurityConfig.class)
    static class TestApplication {
    }
}
