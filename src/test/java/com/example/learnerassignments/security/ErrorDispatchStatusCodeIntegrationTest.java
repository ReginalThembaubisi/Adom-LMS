package com.example.learnerassignments.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Status codes as a real servlet container actually returns them.
 *
 * MockMvc does not perform the ERROR dispatch, so it cannot see what happens after
 * {@code sendError} — and Spring Security reports an access denial that way. In a real
 * container the container re-runs the whole filter chain for {@code /error}, with no
 * authentication on that second pass; if the chain does not permit the ERROR dispatch type,
 * a genuine 403 is re-denied as anonymous and rewritten by the entry point into a 401.
 *
 * That is not a cosmetic difference. The portal's authFetch treats 401 as "your session is
 * gone" and clears it, so a learner who touched any staff endpoint was silently logged out.
 * It passed every MockMvc test we had. This runs against Tomcat so it cannot again.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class ErrorDispatchStatusCodeIntegrationTest {

    @Autowired TestRestTemplate rest;
    @Autowired ObjectMapper objectMapper;
    @LocalServerPort int port;

    @MockBean JavaMailSender mailSender;

    private static final String PASSWORD = "Rehearsal#2026";

    private String learnerToken;

    @BeforeEach
    void registerAndLogIn() throws Exception {
        HttpHeaders json = new HttpHeaders();
        json.setContentType(MediaType.APPLICATION_JSON);

        String registration = """
                {"fullName":"Container Fixture","email":"container-%d@example.com",\
                "phoneNumber":"0821234567","cohort":"2026-01","password":"%s"}
                """.formatted(System.nanoTime(), PASSWORD);

        ResponseEntity<String> registered = rest.exchange(
                "/api/learners", HttpMethod.POST, new HttpEntity<>(registration, json), String.class);
        assertThat(registered.getStatusCode())
                .as("registration failed, so the rest of this class proves nothing: %s", registered.getBody())
                .isEqualTo(HttpStatus.CREATED);

        JsonNode body = objectMapper.readTree(registered.getBody());
        learnerToken = body.get("token").asText();
        assertThat(learnerToken).isNotBlank();
    }

    private HttpEntity<Void> asLearner() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(learnerToken);
        return new HttpEntity<>(headers);
    }

    @Test
    @DisplayName("a learner denied a staff endpoint gets 403, not a session-clearing 401")
    void roleDenialSurvivesTheErrorDispatch() {
        ResponseEntity<String> response = rest.exchange(
                "/api/learners", HttpMethod.GET, asLearner(), String.class);

        assertThat(response.getStatusCode())
                .as("401 here would make the portal drop a perfectly good session")
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("an authenticated learner still reads their own record")
    void ownRecordIsReadable() {
        ResponseEntity<String> response = rest.exchange(
                "/api/me", HttpMethod.GET, asLearner(), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("Container Fixture");
    }

    @Test
    @DisplayName("no credentials is still 401, and a real 401 must stay distinguishable from a 403")
    void anonymousIsStillUnauthorized() {
        ResponseEntity<String> response = rest.exchange(
                "/api/me", HttpMethod.GET, HttpEntity.EMPTY, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("a not-found from the exception advice is unaffected by the dispatch rules")
    void notFoundStaysNotFound() {
        ResponseEntity<String> response = rest.exchange(
                "/api/me/modules/999999", HttpMethod.GET, asLearner(), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("the legacy public uploads path is closed in a real container too")
    void uploadsRequireAuthentication() {
        ResponseEntity<String> response = rest.exchange(
                "/uploads/202600001_1_amanda-report.pdf", HttpMethod.GET, HttpEntity.EMPTY, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
