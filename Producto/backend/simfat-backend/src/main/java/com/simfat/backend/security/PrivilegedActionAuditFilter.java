package com.simfat.backend.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Set;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class PrivilegedActionAuditFilter extends OncePerRequestFilter {

    private static final Set<String> MUTATING_METHODS = Set.of("POST", "PUT", "PATCH", "DELETE");

    private final SecurityAuditService securityAuditService;

    public PrivilegedActionAuditFilter(SecurityAuditService securityAuditService) {
        this.securityAuditService = securityAuditService;
    }

    @Override
    protected void doFilterInternal(
        HttpServletRequest request,
        HttpServletResponse response,
        FilterChain filterChain
    ) throws ServletException, IOException {
        filterChain.doFilter(request, response);

        if (!MUTATING_METHODS.contains(request.getMethod())) {
            return;
        }

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return;
        }

        boolean privileged = authentication.getAuthorities().stream()
            .map(GrantedAuthority::getAuthority)
            .anyMatch(this::isPrivilegedAuthority);
        if (!privileged) {
            return;
        }

        securityAuditService.auditPrivilegedAction(
            request.getMethod(),
            request.getRequestURI(),
            response.getStatus(),
            authentication
        );
    }

    private boolean isPrivilegedAuthority(String authority) {
        if (authority == null || authority.isBlank()) {
            return false;
        }
        return authority.startsWith("PERM_")
            || "ROLE_ADMIN".equals(authority)
            || "ROLE_SUPER_ADMIN".equals(authority)
            || "ROLE_MODERATOR".equals(authority);
    }
}
