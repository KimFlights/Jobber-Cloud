package cs590.ResumeService.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Defense-in-depth authentication. Cognito already validates the JWT at the edge (API Gateway),
 * but this service validates it a <em>second</em> time against the Cognito pool's JWKS so it never
 * relies on the network boundary alone: a request to {@code /api/**} is rejected unless it carries a
 * genuine, unexpired token. Identity ({@code sub}) is then taken from the verified token — see
 * {@link JwtSubHeaderFilter} — so the previously-spoofable {@code X-User-Sub} header can no longer be
 * forged.
 *
 * <p>Toggled by {@code security.jwt.enabled}. On AWS the AppConfig profile sets it {@code true} plus
 * the issuer URI; locally the property is absent, so the {@link JwtDisabled} permit-all chain keeps
 * docker-compose on the pass-through stub with no tokens required.
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
                    // Internal service-to-service read (SearchService -> embedding). Keyed by path,
                    // reached only over the private network via Cloud Map, returns just a vector.
                    // It carries no user token, so it stays open; the edge never routes it publicly.
                    .requestMatchers(HttpMethod.GET, "/api/resumes/*/embedding").permitAll()
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