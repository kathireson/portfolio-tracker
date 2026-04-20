package com.homelab.portfolio.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.security.oauth2.client.servlet.OAuth2ClientAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    /**
     * OAuth2/Keycloak security — active when portfolio.oauth.enabled=true (the
     * default).
     */
    @Configuration
    @ConditionalOnProperty(name = "portfolio.oauth.enabled", havingValue = "true", matchIfMissing = true)
    @ImportAutoConfiguration(OAuth2ClientAutoConfiguration.class)
    static class OAuthSecurityConfig {

        @Value("${KEYCLOAK_ISSUER_URI}")
        private String keycloakIssuerUri;

        @Bean
        public JwtDecoder jwtDecoder() {
            return NimbusJwtDecoder.withJwkSetUri(keycloakIssuerUri)
                    .build();
        }

        @Bean
        public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
            http
                    .authorizeHttpRequests(auth -> auth
                            .requestMatchers("/login", "/oauth2/authorization/**", "/logout", "/oauth2/logout/**",
                                    "/h2-console/**")
                            .permitAll()
                            .requestMatchers("/static/**", "/resources/**", "/css/**", "/favicon.ico").permitAll()
                            .anyRequest().authenticated())
                    .oauth2Login(Customizer.withDefaults())
                    .logout(logout -> logout.logoutSuccessUrl("/"))
                    .csrf(AbstractHttpConfigurer::disable);
            return http.build();
        }
    }

    /**
     * No-auth security — active when portfolio.oauth.enabled=false.
     * Permits all requests without authentication.
     */
    @Configuration
    @ConditionalOnProperty(name = "portfolio.oauth.enabled", havingValue = "false")
    static class NoAuthSecurityConfig {

        @Bean
        public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
            http
                    .authorizeHttpRequests(auth -> auth
                            .anyRequest().permitAll())
                    .csrf(AbstractHttpConfigurer::disable);
            return http.build();
        }
    }
}
