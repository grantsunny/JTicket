package com.jticket.security;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.util.matcher.NegatedRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

@Configuration
@Profile("production")
public class OAuth2SecurityConfig {

    private static final RequestMatcher API_REQUESTS = new AntPathRequestMatcher("/api/**");

    @Bean
    SecurityFilterChain oauth2SecurityFilterChain(
            HttpSecurity http,
            JwtAuthenticationConverter jwtAuthenticationConverter,
            OAuth2UserService<OidcUserRequest, OidcUser> oidcUserService,
            OAuth2AuthorizationRequestResolver authorizationRequestResolver) throws Exception {

        http
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/oauth2/**", "/login/**", "/error").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/events/**").hasAuthority("SCOPE_event:read")
                        .requestMatchers(HttpMethod.POST, "/api/events/**").hasAuthority("SCOPE_event:write")
                        .requestMatchers(HttpMethod.PUT, "/api/events/**").hasAuthority("SCOPE_event:write")
                        .requestMatchers(HttpMethod.PATCH, "/api/events/**").hasAuthority("SCOPE_event:write")
                        .requestMatchers(HttpMethod.DELETE, "/api/events/**").hasAuthority("SCOPE_event:write")
                        .requestMatchers(HttpMethod.GET, "/api/venues/**").hasAuthority("SCOPE_venue:read")
                        .requestMatchers(HttpMethod.GET, "/api/seats/**").hasAuthority("SCOPE_seat:read")
                        .requestMatchers("/api/template", "/api/template/**").hasAuthority("SCOPE_template:write")
                        .requestMatchers("/api/orders", "/api/orders/**").hasAuthority("SCOPE_order:write")
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().authenticated())
                // The current browser UI mutates /api resources without a CSRF token.
                .csrf(AbstractHttpConfigurer::disable)
                .oauth2Login(oauth2 -> oauth2
                        .authorizationEndpoint(endpoint -> endpoint
                                .authorizationRequestResolver(authorizationRequestResolver))
                        .userInfoEndpoint(endpoint -> endpoint
                                .oidcUserService(oidcUserService)))
                .oauth2ResourceServer(resourceServer -> resourceServer
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter)))
                .exceptionHandling(exceptions -> exceptions
                        .defaultAuthenticationEntryPointFor(
                                new BearerTokenAuthenticationEntryPoint(),
                                API_REQUESTS)
                        .defaultAuthenticationEntryPointFor(
                                new LoginUrlAuthenticationEntryPoint("/oauth2/authorization/jticket"),
                                new NegatedRequestMatcher(API_REQUESTS)));

        return http.build();
    }

    @Bean
    OAuth2AuthorizationRequestResolver authorizationRequestResolver(
            ClientRegistrationRepository clientRegistrations,
            @Value("${ticket.oauth2.audience}") String audience) {

        DefaultOAuth2AuthorizationRequestResolver resolver =
                new DefaultOAuth2AuthorizationRequestResolver(
                        clientRegistrations,
                        "/oauth2/authorization");

        resolver.setAuthorizationRequestCustomizer(request -> request
                .additionalParameters(parameters -> parameters.put("audience", audience)));
        return resolver;
    }

    @Bean
    JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter scopes = new JwtGrantedAuthoritiesConverter();

        JwtGrantedAuthoritiesConverter permissions = new JwtGrantedAuthoritiesConverter();
        permissions.setAuthoritiesClaimName("permissions");
        permissions.setAuthorityPrefix("SCOPE_");

        Converter<Jwt, Collection<GrantedAuthority>> authoritiesConverter = jwt -> {
            Collection<GrantedAuthority> authorities = new LinkedHashSet<>(scopes.convert(jwt));
            authorities.addAll(permissions.convert(jwt));
            return authorities;
        };

        JwtAuthenticationConverter authenticationConverter = new JwtAuthenticationConverter();
        authenticationConverter.setJwtGrantedAuthoritiesConverter(authoritiesConverter);
        return authenticationConverter;
    }

    @Bean
    OAuth2UserService<OidcUserRequest, OidcUser> oidcUserService(
            JwtDecoder jwtDecoder,
            JwtAuthenticationConverter jwtAuthenticationConverter) {

        OidcUserService delegate = new OidcUserService();

        return userRequest -> {
            OidcUser oidcUser = delegate.loadUser(userRequest);
            Collection<GrantedAuthority> authorities =
                    new LinkedHashSet<>(oidcUser.getAuthorities());

            Set<String> tokenScopes = userRequest.getAccessToken().getScopes();
            tokenScopes.stream()
                    .map(scope -> new SimpleGrantedAuthority("SCOPE_" + scope))
                    .forEach(authorities::add);

            try {
                Jwt accessToken = jwtDecoder.decode(userRequest.getAccessToken().getTokenValue());
                Authentication authentication = jwtAuthenticationConverter.convert(accessToken);
                if (authentication != null) {
                    authorities.addAll(authentication.getAuthorities());
                }
            } catch (JwtException ignored) {
                // Some OIDC providers issue opaque browser access tokens. Keep login usable
                // while still applying any authorities carried in the token response scopes.
            }

            return new DefaultOidcUser(
                    authorities,
                    oidcUser.getIdToken(),
                    oidcUser.getUserInfo(),
                    userNameAttributeName(userRequest));
        };
    }

    private static String userNameAttributeName(OidcUserRequest userRequest) {
        String configuredNameAttribute = userRequest.getClientRegistration()
                .getProviderDetails()
                .getUserInfoEndpoint()
                .getUserNameAttributeName();

        return configuredNameAttribute == null || configuredNameAttribute.isBlank()
                ? "sub"
                : configuredNameAttribute;
    }
}
