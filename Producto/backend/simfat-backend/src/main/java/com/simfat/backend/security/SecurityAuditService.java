package com.simfat.backend.security;

import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;

@Service
public class SecurityAuditService {

    private static final Logger LOGGER = LoggerFactory.getLogger(SecurityAuditService.class);

    public void auditPrivilegedAction(
        String method,
        String path,
        int status,
        Authentication authentication
    ) {
        String userId = "anonymous";
        if (authentication != null && authentication.getPrincipal() instanceof AppUserPrincipal principal) {
            userId = principal.getUserId();
        }

        Set<String> authorities = authentication == null
            ? Set.of()
            : authentication.getAuthorities().stream().map(GrantedAuthority::getAuthority).collect(java.util.stream.Collectors.toSet());

        LOGGER.info(
            "security_audit event=PRIVILEGED_ACTION userId={} method={} path={} status={} authorities={}",
            userId,
            method,
            path,
            status,
            authorities
        );
    }
}
