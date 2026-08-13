package cs590.SearchService.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Runs after JWT authentication and forces {@code X-User-Sub} to the verified token's {@code sub}.
 * Controllers keep reading the header exactly as before, but its value is now the cryptographically
 * validated subject and any client-supplied {@code X-User-Sub} is ignored. Only wired in when JWT
 * auth is enabled.
 */
public class JwtSubHeaderFilter extends OncePerRequestFilter {

    private static final String USER_HEADER = "X-User-Sub";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof JwtAuthenticationToken jwt && jwt.getToken().getSubject() != null) {
            filterChain.doFilter(new SubHeaderRequest(request, jwt.getToken().getSubject()), response);
        } else {
            filterChain.doFilter(request, response);
        }
    }

    /** Overrides only the {@code X-User-Sub} header; every other header passes through untouched. */
    private static final class SubHeaderRequest extends HttpServletRequestWrapper {
        private final String sub;

        SubHeaderRequest(HttpServletRequest request, String sub) {
            super(request);
            this.sub = sub;
        }

        @Override
        public String getHeader(String name) {
            return USER_HEADER.equalsIgnoreCase(name) ? sub : super.getHeader(name);
        }

        @Override
        public Enumeration<String> getHeaders(String name) {
            return USER_HEADER.equalsIgnoreCase(name)
                    ? Collections.enumeration(List.of(sub))
                    : super.getHeaders(name);
        }

        @Override
        public Enumeration<String> getHeaderNames() {
            List<String> names = Collections.list(super.getHeaderNames());
            if (names.stream().noneMatch(USER_HEADER::equalsIgnoreCase)) {
                names.add(USER_HEADER);
            }
            return Collections.enumeration(names);
        }
    }
}