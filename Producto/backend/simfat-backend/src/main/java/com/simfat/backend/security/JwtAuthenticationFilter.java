package com.simfat.backend.security;

import com.simfat.backend.model.AppUser;
import com.simfat.backend.repository.AppUserRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.stream.Collectors;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final AppUserRepository appUserRepository;
    private final AuthenticationEntryPoint authenticationEntryPoint;

    public JwtAuthenticationFilter(
        JwtService jwtService,
        AppUserRepository appUserRepository,
        AuthenticationEntryPoint authenticationEntryPoint
    ) {
        this.jwtService = jwtService;
        this.appUserRepository = appUserRepository;
        this.authenticationEntryPoint = authenticationEntryPoint;
    }

    @Override
    protected void doFilterInternal(
        HttpServletRequest request,
        HttpServletResponse response,
        FilterChain filterChain
    ) throws ServletException, IOException {
        String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = authHeader.substring(7);
        try {
            Jws<Claims> access = jwtService.parseAndValidate(token, JwtService.TOKEN_TYPE_ACCESS);
            AppUser user = appUserRepository.findById(access.getPayload().getSubject()).orElse(null);
            if (user == null || !user.isEnabled()) {
                throw new BadCredentialsException("Token invalido");
            }
            AppUserPrincipal principal = new AppUserPrincipal(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                user.getRoles()
            );

            UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                principal,
                null,
                user.getRoles()
                    .stream()
                    .map(role -> new SimpleGrantedAuthority("ROLE_" + role.name()))
                    .collect(Collectors.toSet())
            );
            SecurityContextHolder.getContext().setAuthentication(authentication);
            filterChain.doFilter(request, response);
        } catch (JwtException | IllegalArgumentException ex) {
            if (isInternalOpenEoIngestPath(request)) {
                SecurityContextHolder.clearContext();
                filterChain.doFilter(request, response);
                return;
            }
            SecurityContextHolder.clearContext();
            authenticationEntryPoint.commence(request, response, new BadCredentialsException("Token invalido", ex));
        }
    }

    private boolean isInternalOpenEoIngestPath(HttpServletRequest request) {
        return "/api/indicators/measurements".equals(request.getRequestURI());
    }
}
