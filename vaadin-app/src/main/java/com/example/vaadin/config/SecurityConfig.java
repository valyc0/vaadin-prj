package com.example.vaadin.config;

import com.vaadin.flow.spring.security.VaadinWebSecurity;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.client.oidc.web.logout.OidcClientInitiatedLogoutSuccessHandler;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.web.authentication.logout.LogoutSuccessHandler;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

/**
 * Security configuration for OAuth2 and OIDC with Keycloak
 */
@EnableWebSecurity
@Configuration
public class SecurityConfig extends VaadinWebSecurity {

    private final ClientRegistrationRepository clientRegistrationRepository;

    public SecurityConfig(ClientRegistrationRepository clientRegistrationRepository) {
        this.clientRegistrationRepository = clientRegistrationRepository;
    }

    @Bean
    public LogoutSuccessHandler oidcLogoutSuccessHandler() {
        OidcClientInitiatedLogoutSuccessHandler logoutSuccessHandler = 
            new OidcClientInitiatedLogoutSuccessHandler(clientRegistrationRepository);
        logoutSuccessHandler.setPostLogoutRedirectUri("{baseUrl}");
        return logoutSuccessHandler;
    }

    @Override
    protected void configure(HttpSecurity http) throws Exception {
        // Configure exceptions for Vaadin static resources
        http.authorizeHttpRequests(auth -> 
            auth.requestMatchers(
                new AntPathRequestMatcher("/images/*.png"),
                new AntPathRequestMatcher("/line-awesome/**/*.svg"),
                new AntPathRequestMatcher("/VAADIN/**"),
                new AntPathRequestMatcher("/oauth2/**")
            ).permitAll()
        );

        // Configure OAuth2 login
        http.oauth2Login(oauth2 -> oauth2
            .loginPage("/oauth2/authorization/keycloak")
            .defaultSuccessUrl("/", true)
        );

        // Configure OIDC logout with Keycloak
        http.logout(logout -> logout
            .logoutSuccessHandler(oidcLogoutSuccessHandler())
            .invalidateHttpSession(true)
            .clearAuthentication(true)
            .deleteCookies("JSESSIONID")
        );

        // Apply Vaadin base configuration
        super.configure(http);
    }
}
