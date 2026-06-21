package com.jticket.staticui;

import static org.assertj.core.api.Assertions.assertThat;

import com.jticket.StaticResourceConfig;
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
                .contains("<title>Welcome to StoneTicket!</title>")
                .contains("welcome to JTicket!")
                .contains("href=\"/venue.html\"")
                .contains("href=\"/event.html\"");
    }

    @Test
    void indexHtmlIsReachableFromStaticPath() {
        ResponseEntity<String> response = restTemplate.getForEntity("/static/index.html", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody())
                .contains("<title>Welcome to StoneTicket!</title>")
                .contains("welcome to JTicket!")
                .contains("href=\"/venue.html\"")
                .contains("href=\"/event.html\"");
    }

    @Test
    void venueHtmlIsReachableFromStaticPath() {
        ResponseEntity<String> response = restTemplate.getForEntity("/static/venue.html", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody())
                .contains("<title>Welcome to StoneTicket!</title>")
                .contains("Upload venue template")
                .contains("id=\"venuePreview\"")
                .contains("id=\"seatsContainer\"");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "/static/script.js",
            "/static/style.css",
            "/static/modal-style.css"
    })
    void linkedStaticAssetsAreReachable(String path) {
        ResponseEntity<String> response = restTemplate.getForEntity(path, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotBlank();
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
    @Import({DevelopmentSecurityConfig.class, StaticResourceConfig.class})
    static class TestApplication {
    }
}
