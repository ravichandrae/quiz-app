package org.schoolmela.quiz.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Clock;
import java.util.Set;
import org.schoolmela.quiz.common.RateLimiter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Limits login, registration and token refresh per client network address, to slow down PIN
 * guessing spread across many accounts. (Each account also locks after a few wrong PINs.)
 * The client address comes from Tomcat's forwarded-header handling, which only trusts proxies on
 * private networks, so clients cannot dodge the limit with a made-up X-Forwarded-For header.
 */
@Component
public class AuthRateLimitFilter extends OncePerRequestFilter {

    private static final Set<String> LIMITED_PATHS = Set.of("/auth/login", "/auth/register", "/auth/refresh");
    private static final String BODY = """
            {"status":429,"code":"RATE_LIMITED","detail":"Too many tries from this network. Please wait a minute and try again."}""";

    private final RateLimiter limiter;

    public AuthRateLimitFilter(RateLimitProperties props, Clock clock) {
        this.limiter = new RateLimiter(props.authRequestsPerMinute(), clock);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !"POST".equals(request.getMethod()) || !LIMITED_PATHS.contains(request.getServletPath());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        long waitSeconds = limiter.acquire(request.getRemoteAddr());
        if (waitSeconds == 0) {
            chain.doFilter(request, response);
            return;
        }
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(waitSeconds));
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.getWriter().write(BODY);
    }
}
