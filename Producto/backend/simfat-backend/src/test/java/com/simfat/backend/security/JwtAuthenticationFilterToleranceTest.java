package com.simfat.backend.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.simfat.backend.model.AppUser;
import com.simfat.backend.repository.AppUserRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import jakarta.servlet.FilterChain;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.AuthenticationEntryPoint;

/**
 * Behaviour of the JWT filter when a validly signed token cannot be turned into an authentication
 * (unknown or disabled user, failing lookup) and when downstream code fails.
 */
class JwtAuthenticationFilterToleranceTest {

    private static final String PUBLIC_PATH = "/api/territory/public/layers";
    private static final String PROTECTED_PATH = "/api/community/contacts";

    private JwtService jwtService;
    private AppUserRepository appUserRepository;
    private AuthorizationResolverService authorizationResolverService;
    private AuthenticationEntryPoint entryPoint;
    private JwtAuthenticationFilter filter;
    private FilterChain chain;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        SecurityContextHolder.clearContext();
        jwtService = mock(JwtService.class);
        appUserRepository = mock(AppUserRepository.class);
        authorizationResolverService = mock(AuthorizationResolverService.class);
        entryPoint = mock(AuthenticationEntryPoint.class);
        chain = mock(FilterChain.class);
        filter = new JwtAuthenticationFilter(jwtService, appUserRepository, authorizationResolverService, entryPoint);

        Jws<Claims> jws = mock(Jws.class);
        Claims claims = mock(Claims.class);
        when(jws.getPayload()).thenReturn(claims);
        when(claims.getSubject()).thenReturn("user-1");
        when(jwtService.parseAndValidate(any(), eq(JwtService.TOKEN_TYPE_ACCESS))).thenReturn(jws);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void validTokenForUnknownUserOnPublicPathContinuesAnonymously() throws Exception {
        when(appUserRepository.findById("user-1")).thenReturn(Optional.empty());
        MockHttpServletRequest request = requestTo(PUBLIC_PATH);

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        verify(chain, times(1)).doFilter(any(), any());
        verify(entryPoint, never()).commence(any(), any(), any());
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void validTokenForDisabledUserOnPublicPathContinuesAnonymously() throws Exception {
        AppUser disabled = mock(AppUser.class);
        when(disabled.isEnabled()).thenReturn(false);
        when(appUserRepository.findById("user-1")).thenReturn(Optional.of(disabled));

        filter.doFilter(requestTo(PUBLIC_PATH), new MockHttpServletResponse(), chain);

        verify(chain, times(1)).doFilter(any(), any());
        verify(entryPoint, never()).commence(any(), any(), any());
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void validTokenForUnknownUserOnProtectedPathReturnsUnauthorized() throws Exception {
        when(appUserRepository.findById("user-1")).thenReturn(Optional.empty());

        filter.doFilter(requestTo(PROTECTED_PATH), new MockHttpServletResponse(), chain);

        verify(entryPoint, times(1)).commence(any(), any(), any(BadCredentialsException.class));
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    void lookupFailureOnPublicPathContinuesAnonymously() throws Exception {
        when(appUserRepository.findById("user-1")).thenThrow(new DataAccessResourceFailureException("mongo down"));

        filter.doFilter(requestTo(PUBLIC_PATH), new MockHttpServletResponse(), chain);

        verify(chain, times(1)).doFilter(any(), any());
        verify(entryPoint, never()).commence(any(), any(), any());
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void lookupFailureOnProtectedPathSurfacesAsErrorNotAsAnonymous() throws Exception {
        when(appUserRepository.findById("user-1")).thenThrow(new DataAccessResourceFailureException("mongo down"));

        assertThatThrownBy(() -> filter.doFilter(requestTo(PROTECTED_PATH), new MockHttpServletResponse(), chain))
            .isInstanceOf(DataAccessResourceFailureException.class);

        verify(chain, never()).doFilter(any(), any());
        verify(entryPoint, never()).commence(any(), any(), any());
    }

    @Test
    void downstreamIllegalArgumentExceptionDoesNotRunTheChainTwice() throws Exception {
        givenEnabledUser();
        doThrow(new IllegalArgumentException("boom from handler")).when(chain).doFilter(any(), any());

        assertThatThrownBy(() -> filter.doFilter(requestTo(PUBLIC_PATH), new MockHttpServletResponse(), chain))
            .isInstanceOf(IllegalArgumentException.class);

        verify(chain, times(1)).doFilter(any(), any());
        verify(entryPoint, never()).commence(any(), any(), any());
    }

    @Test
    void validTokenForEnabledUserOnPublicPathAuthenticatesNormally() throws Exception {
        givenEnabledUser();
        // Capture the authentication as seen by downstream code.
        Object[] seen = new Object[1];
        doAnswerCapturing(seen);

        filter.doFilter(requestTo(PUBLIC_PATH), new MockHttpServletResponse(), chain);

        verify(chain, times(1)).doFilter(any(), any());
        assertThat(seen[0]).isNotNull();
    }

    private void givenEnabledUser() {
        AppUser user = mock(AppUser.class);
        when(user.isEnabled()).thenReturn(true);
        when(user.getId()).thenReturn("user-1");
        when(user.getEmail()).thenReturn("user@example.com");
        when(user.getFullName()).thenReturn("Test User");
        when(appUserRepository.findById("user-1")).thenReturn(Optional.of(user));
        Set<GrantedAuthority> authorities = Set.of(new SimpleGrantedAuthority("ROLE_COMMUNITY_USER"));
        when(authorizationResolverService.resolveForUser(user))
            .thenReturn(new AuthorizationSnapshot(Set.of("ROLE_COMMUNITY_USER"), Set.of(), authorities));
    }

    private void doAnswerCapturing(Object[] seen) throws Exception {
        org.mockito.Mockito.doAnswer(invocation -> {
            seen[0] = SecurityContextHolder.getContext().getAuthentication();
            return null;
        }).when(chain).doFilter(any(), any());
    }

    private MockHttpServletRequest requestTo(String path) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
        request.addHeader("Authorization", "Bearer signed.but.stale");
        return request;
    }
}
