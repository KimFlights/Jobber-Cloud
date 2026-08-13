package cs590.ScraperService.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Defense-in-depth authentication for the manual scrape trigger ({@code POST /api/scrape}). Cognito
 * validates the JWT at the edge; this service validates it again against the Cognito pool's JWKS so
 * the endpoint is rejected without a genuine token, never relying on the network boundary alone.
 * (Unlike Resume/Search this service reads no {@code X-User-Sub}, so no header-injection filter is
 * needed — it only gates access.)
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
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()));
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
