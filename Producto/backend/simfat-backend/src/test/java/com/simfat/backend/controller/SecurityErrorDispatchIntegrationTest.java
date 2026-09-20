package com.simfat.backend.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.simfat.backend.model.AppUser;
import com.simfat.backend.repository.AppUserRepository;
import com.simfat.backend.repository.RegionRepository;
import com.simfat.backend.security.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

/**
 * Runs on a real embedded server, because MockMvc does not perform the container ERROR dispatch.
 * Asserts that anonymous callers on public paths are not answered with 401/403, that protected
 * paths still answer 401 to anonymous callers, and that an infrastructure failure on a protected
 * path (which reaches the container ERROR dispatch) is answered with 500 rather than 401.
 * The missing-upload case currently ends in a 500 from GlobalExceptionHandler (known issue,
 * deferred), so only the absence of 401/403 is asserted there.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SecurityErrorDispatchIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private JwtService jwtService;

    // Keeps the startup runner (MonitoredRegionsConfig) off a real Mongo.
    @MockBean
    private RegionRepository regionRepository;

    @MockBean
    private AppUserRepository appUserRepository;

    @Test
    void missingUploadOnPublicPathIsNotAnsweredWithUnauthorizedOrForbidden() {
        ResponseEntity<String> response = restTemplate.getForEntity("/uploads/citizen-reports/does-not-exist.jpg", String.class);
        assertThat(response.getStatusCode().value()).isNotIn(401, 403);
    }

    @Test
    void protectedPathStillReturnsUnauthorizedToAnonymousCallers() {
        ResponseEntity<String> response = restTemplate.getForEntity("/api/community/contacts", String.class);
        assertThat(response.getStatusCode().value()).isEqualTo(401);
    }

    @Test
    void infrastructureFailureOnProtectedPathWithValidTokenIsAnsweredWith500NotUnauthorized() {
        AppUser user = new AppUser();
        user.setId("user-1");
        user.setEmail("user@example.com");
        user.setFullName("Test User");
        String token = jwtService.generateTokenPair(user).getAccessToken();
        // The filter rethrows infrastructure failures on protected paths; Tomcat then performs the ERROR dispatch.
        when(appUserRepository.findById(anyString())).thenThrow(new RuntimeException("simulated datastore outage"));

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        ResponseEntity<String> response = restTemplate.exchange(
            "/api/community/contacts", HttpMethod.GET, new HttpEntity<>(headers), String.class);

        assertThat(response.getStatusCode().value()).isEqualTo(500);
    }
}
