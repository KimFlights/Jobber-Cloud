package cs590.SearchService.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Defense-in-depth authentication. Cognito validates the JWT at the edge (API Gateway); this service
 * validates it again against the Cognito pool's JWKS so {@code /api/**} is rejected without a genuine
 * token, never relying on the network boundary alone. Identity ({@code sub}) is taken from the
 * verified token via {@link JwtSubHeaderFilter}, so {@code X-User-Sub} can no longer be spoofed.
 *
 * <p>Toggled by {@code security.jwt.enabled}: AWS AppConfig sets it {@code true} plus the issuer URI;
 * locally the property is absent, so the {@link JwtDisabled} permit-all chain preserves the stub.
 */
@Configuration
public class SecurityConfig {

    @Configuration
    @EnableWebSecurity
    @ConditionalOnProperty(name = "security.jwt.enabled", havingValue = "true")
    static class JwtEnabled {
        @Bean
        SecurityFilterChain jwtFilterChain(HttpSecurity http) throws Exception {
            http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                    .requestMatchers("/actuator/**", "/appconfig").permitAll()
                    .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()))
                .addFilterAfter(new JwtSubHeaderFilter(), BearerTokenAuthenticationFilter.class);
            return http.build();
        }
    }

    @Configuration
    @EnableWebSecurity
    @ConditionalOnProperty(name = "security.jwt.enabled", havingValue = "false", matchIfMissing = true)
    static class JwtDisabled {
        @Bean
        SecurityFilterChain openFilterChain(HttpSecurity http) throws Exception {
            http
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
            return http.build();
        }
    }
}