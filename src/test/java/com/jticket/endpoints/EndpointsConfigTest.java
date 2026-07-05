package com.jticket.endpoints;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.glassfish.jersey.server.ServerProperties;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;

class EndpointsConfigTest {

    @Test
    void disablesJerseyWadlGeneration() {
        Environment environment = mock(Environment.class);
        when(environment.acceptsProfiles(any(Profiles.class))).thenReturn(false);

        EndpointsConfig config = new EndpointsConfig(environment);

        assertThat(config.getProperty(ServerProperties.WADL_FEATURE_DISABLE)).isEqualTo(true);
    }
}
