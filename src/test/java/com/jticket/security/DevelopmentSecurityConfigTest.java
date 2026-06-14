package com.jticket.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@WebMvcTest(properties = "spring.profiles.active=dev")
@ContextConfiguration(classes = {
        DevelopmentSecurityConfig.class,
        DevelopmentSecurityConfigTest.TestEndpoint.class
})
class DevelopmentSecurityConfigTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void permitsUnauthenticatedDevelopmentRequests() throws Exception {
        mockMvc.perform(get("/api/test"))
                .andExpect(status().isOk());
    }

    @RestController
    static class TestEndpoint {

        @GetMapping("/api/test")
        String api() {
            return "api";
        }
    }
}
