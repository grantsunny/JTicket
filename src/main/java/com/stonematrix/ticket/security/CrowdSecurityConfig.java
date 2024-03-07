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
import jakarta.enterprise.inject.Any;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
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
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;

import java.util.Properties;

@Configuration
@EnableWebSecurity
@Profile("production")
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
    public SecurityFilterChain filterChainBackOffice(HttpSecurity http) throws Exception {

            http.authorizeHttpRequests(authorizeRequests -> authorizeRequests
                .requestMatchers("/login", "/login?error", "/login?logout").permitAll()
                .anyRequest().authenticated())
            .formLogin(Customizer.withDefaults())
            .httpBasic(httpBasic -> httpBasic
                    .authenticationEntryPoint(
                            new LoginUrlAuthenticationEntryPoint("/login")));

        return http.authenticationProvider(crowdAuthenticationProvider()).build();
    }

    @Bean ClientProperties clientProperties() {

        if (clientProperties != null)
            return clientProperties;

        Properties crowdProperties = new Properties();
        crowdProperties.setProperty("application.name", env.getProperty("crowd.application.name"));
        crowdProperties.setProperty("application.password", env.getProperty("crowd.application.password"));
        crowdProperties.setProperty("crowd.server.url", env.getProperty("crowd.server.url"));
        crowdProperties.setProperty("session.validationinterval", env.getProperty("crowd.session.validationinterval"));

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
