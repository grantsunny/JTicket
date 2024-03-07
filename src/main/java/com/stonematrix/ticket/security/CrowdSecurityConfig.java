package com.stonematrix.ticket.security;

import com.atlassian.crowd.integration.http.CrowdHttpAuthenticator;
import com.atlassian.crowd.integration.http.CrowdHttpAuthenticatorImpl;
import com.atlassian.crowd.integration.http.util.CrowdHttpTokenHelper;
import com.atlassian.crowd.integration.http.util.CrowdHttpTokenHelperImpl;
import com.atlassian.crowd.integration.http.util.CrowdHttpValidationFactorExtractor;
import com.atlassian.crowd.integration.http.util.CrowdHttpValidationFactorExtractorImpl;
import com.atlassian.crowd.integration.rest.service.factory.RestCrowdClientFactory;
import com.atlassian.crowd.integration.springsecurity.CrowdAuthenticationProvider;
import com.atlassian.crowd.integration.springsecurity.CrowdSSOAuthenticationDetails;
import com.atlassian.crowd.integration.springsecurity.RemoteCrowdAuthenticationProvider;
import com.atlassian.crowd.integration.springsecurity.user.CrowdUserDetailsService;
import com.atlassian.crowd.integration.springsecurity.user.CrowdUserDetailsServiceImpl;
import com.atlassian.crowd.service.client.ClientProperties;
import com.atlassian.crowd.service.client.ClientPropertiesImpl;
import com.atlassian.crowd.service.client.CrowdClient;

import com.atlassian.crowd.service.factory.CrowdClientFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.core.env.Environment;

import jakarta.inject.Inject;
import java.util.Properties;

@Configuration
@EnableWebSecurity
//@Profile("production")
public class CrowdSecurityConfig {
    @Inject
    private Environment env;

    private ClientProperties clientProperties;
    private CrowdClientFactory crowdClientFactory;
    private CrowdClient crowdClient;
    private CrowdHttpValidationFactorExtractor validationFactorExtractor;
    private CrowdHttpTokenHelper tokenHelper;
    private CrowdHttpAuthenticator crowdHttpAuthenticator;
    private CrowdUserDetailsService crowdUserDetailsService;
    private CrowdAuthenticationProvider crowdAuthenticationProvider;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http.formLogin(Customizer.withDefaults());

        http.authorizeHttpRequests(authorizeRequests -> authorizeRequests
                                .anyRequest().authenticated()
                ).formLogin(Customizer.withDefaults());

        return http.authenticationProvider(crowdAuthenticationProvider()).build();
    }

    /**
     * application.name=mySecuredApp
     * application.password=secretPassKey
     * crowd.server.url=https://domain.org/crowd/services/
     * crowd.base.url=https://domain.org/crowd/
     * application.login.url=https://domain.org/mySecuredApp/login
     * cookie.tokenkey=crowd.token
     * session.isauthenticated=SSO_IS_AUTHENTICATED
     * session.tokenkey=CROWD_TOKEN
     * session.validationinterval=0
     * session.lastvalidation=SSO_LAST_VALIDATION
     * @return
     */
    @Bean ClientProperties clientProperties() {

        if (clientProperties != null)
            return clientProperties;

        Properties crowdProperties = new Properties();
        crowdProperties.setProperty("application.name", env.getProperty("crowd.application.name"));
        crowdProperties.setProperty("application.password", env.getProperty("crowd.application.password"));
        crowdProperties.setProperty("crowd.server.url", env.getProperty("crowd.server.url"));
        crowdProperties.setProperty("session.validationinterval", env.getProperty("crowd.session.validationinterval"));

        crowdProperties.setProperty("cookie.tokenkey", "crowd.token");
        crowdProperties.setProperty("isauthenticated", "SSO_IS_AUTHENTICATED");
        crowdProperties.setProperty("tokenkey", "CROWD_TOKEN");
        crowdProperties.setProperty("session.lastvalidation", "SSO_LAST_VALIDATION");

        clientProperties = ClientPropertiesImpl.newInstanceFromProperties(crowdProperties);

        return clientProperties;
    }

    @Bean
    public CrowdClientFactory crowdClientFactory() {
        if (crowdClientFactory != null)
            return crowdClientFactory;

        crowdClientFactory = new RestCrowdClientFactory();
        return crowdClientFactory;
    }

    @Bean
    public CrowdClient crowdClient() {
        if (crowdClient != null)
            return crowdClient;

        crowdClient = crowdClientFactory().newInstance(clientProperties());
        return crowdClient;
    }

    @Bean
    public CrowdHttpValidationFactorExtractor validationFactorExtractor() {
        if (validationFactorExtractor != null)
            return validationFactorExtractor;

        validationFactorExtractor = CrowdHttpValidationFactorExtractorImpl.getInstance();
        return validationFactorExtractor;
    }

    @Bean
    public CrowdHttpTokenHelper tokenHelper() {
        if (tokenHelper != null)
            return tokenHelper;

        tokenHelper = CrowdHttpTokenHelperImpl.getInstance(validationFactorExtractor());
        return tokenHelper;
    }

    @Bean
    public CrowdHttpAuthenticator crowdHttpAuthenticator() {
        if (crowdHttpAuthenticator != null)
            return crowdHttpAuthenticator;

        crowdHttpAuthenticator = new CrowdHttpAuthenticatorImpl(crowdClient(), clientProperties(), tokenHelper());
        return crowdHttpAuthenticator;
    }

    @Bean
    public CrowdUserDetailsService crowdUserDetailsService() {
        if (crowdUserDetailsService != null)
            return crowdUserDetailsService;

        CrowdUserDetailsServiceImpl cuds = new CrowdUserDetailsServiceImpl();
        cuds.setCrowdClient(crowdClient());
        cuds.setAuthorityPrefix("ROLE_");
        crowdUserDetailsService = cuds;

        return crowdUserDetailsService;
    }

    @Bean
    public CrowdAuthenticationProvider crowdAuthenticationProvider() {
        if (crowdAuthenticationProvider != null)
            return crowdAuthenticationProvider;

        crowdAuthenticationProvider =  new RemoteCrowdAuthenticationProvider(
                crowdClient(),
                crowdHttpAuthenticator(),
                crowdUserDetailsService()) {
            @Override
            public boolean supports(AbstractAuthenticationToken authenticationToken) {
                return (authenticationToken instanceof UsernamePasswordAuthenticationToken)
                        || super.supports(authenticationToken);
            }
        };

        return crowdAuthenticationProvider;
    }
}
