package cs590.Gateway.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Placeholder for edge authentication. Cognito is the genuinely-deferred piece (ARCHITECTURE.md
 * §5): this is where the gateway will validate the Cognito JWT and forward the token's {@code sub}
 * as {@code X-User-Sub} to the backend services, which trust that header.
 *
 * <p>Today it is a deliberate pass-through: it does not authenticate. If the client already sends
 * {@code X-User-Sub}, the gateway forwards it downstream unchanged, so the per-user flows are
 * exercisable end to end before auth lands. Do NOT treat this as security.
 */
@Component
@Order(0)
public class AuthenticationStubFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(AuthenticationStubFilter.class);
    private static final String USER_HEADER = "X-User-Sub";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        // TODO(cognito): extract & validate the Bearer JWT, then set X-User-Sub from its `sub`.
        if (request.getHeader(USER_HEADER) == null && log.isDebugEnabled()) {
            log.debug("Request to {} has no {} (auth not yet enforced)", request.getRequestURI(), USER_HEADER);
        }
        filterChain.doFilter(request, response);
    }
}
