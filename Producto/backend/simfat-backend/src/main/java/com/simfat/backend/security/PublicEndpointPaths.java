package com.simfat.backend.security;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.http.HttpMethod;
import org.springframework.util.AntPathMatcher;

/**
 * Single source of truth for the endpoints reachable without authentication.
 * Consumed by the security filter chain and by the JWT filter (stale-token tolerance),
 * and mirrored by the default-deny contract test. Adding an entry here requires
 * updating the expected allowlist in that test.
 */
public final class PublicEndpointPaths {

    private static final AntPathMatcher MATCHER = new AntPathMatcher();

    /** A permitAll rule. A null method means any HTTP method. */
    public record Rule(HttpMethod method, String pattern) {
        public boolean matches(String requestMethod, String path) {
            if (method != null && !method.name().equalsIgnoreCase(requestMethod)) {
                return false;
            }
            return MATCHER.match(pattern, path);
        }

        @Override
        public String toString() {
            return (method == null ? "ANY" : method.name()) + " " + pattern;
        }
    }

    public static final List<Rule> RULES = List.of(
        new Rule(HttpMethod.OPTIONS, "/**"),
        // Swagger / OpenAPI stays public (product decision).
        new Rule(null, "/v3/api-docs/**"),
        new Rule(null, "/swagger-ui/**"),
        new Rule(null, "/swagger-ui.html"),
        // Authentication entry points.
        new Rule(HttpMethod.POST, "/api/auth/register"),
        new Rule(HttpMethod.POST, "/api/auth/login"),
        new Rule(HttpMethod.POST, "/api/auth/forgot-password"),
        new Rule(HttpMethod.POST, "/api/auth/reset-password"),
        new Rule(HttpMethod.POST, "/api/auth/refresh"),
        // Guarded by the dev/local profile check in AuthServiceImpl (403 elsewhere).
        new Rule(HttpMethod.POST, "/api/auth/dev/seed-users"),
        // openeo-service ingest; the controller performs its own token check.
        new Rule(HttpMethod.POST, "/api/indicators/measurements"),
        // Anonymous read contract. Explicit per resource: no wildcard over "/api/*/public", so a
        // new controller cannot become anonymous by naming a route "public". The bare path and the
        // "/**" form are both listed because zero-segment "/**" matching is version-sensitive.
        new Rule(HttpMethod.GET, "/api/territory/public"),
        new Rule(HttpMethod.GET, "/api/territory/public/**"),
        new Rule(HttpMethod.GET, "/api/territory/risk-score/**"),
        new Rule(HttpMethod.GET, "/api/territory/geojson/**"),
        new Rule(HttpMethod.GET, "/geojson/**"),
        new Rule(HttpMethod.GET, "/uploads/citizen-reports/**")
    );

    private PublicEndpointPaths() {
    }

    public static boolean isPublic(String method, String path) {
        return RULES.stream().anyMatch(rule -> rule.matches(method, path));
    }

    public static boolean isPublic(HttpServletRequest request) {
        return isPublic(request.getMethod(), request.getRequestURI());
    }
}
