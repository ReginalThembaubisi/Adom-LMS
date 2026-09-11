package com.example.learnerassignments.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MissingServletRequestParameterException;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The policy this class exists to enforce: every exception type this application deliberately
 * throws gets an explicit status, and nothing else gets to show the caller its own message.
 *
 * A plain unit test, not a Spring context — {@link GlobalExceptionHandler} is a plain class with
 * no dependencies, and the fastest way to prove what each handler answers is to call it directly
 * with a constructed exception.
 */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    @DisplayName("A server precondition (IllegalStateException) is 500 with its own message, not the generic one")
    void illegalStateIsInternalServerErrorWithItsOwnMessage() {
        ResponseEntity<Map<String, Object>> response =
                handler.handleIllegalState(new IllegalStateException("Cloudinary is not configured."));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody().get("message")).isEqualTo("Cloudinary is not configured.");
    }

    @Test
    @DisplayName("A bad request (IllegalArgumentException) is 400 with its own message")
    void illegalArgumentIsBadRequest() {
        ResponseEntity<Map<String, Object>> response =
                handler.handleIllegalArgument(new IllegalArgumentException("categoryId is required."));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().get("message")).isEqualTo("categoryId is required.");
    }

    @Test
    @DisplayName("A permission gate (AccessDeniedException) is 403, not the 500 it fell into before this handler existed")
    void accessDeniedIsForbidden() {
        ResponseEntity<Map<String, Object>> response =
                handler.handleAccessDenied(new AccessDeniedException("Only an admin may request this."));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("A not-found (ResourceNotFoundException) is 404")
    void resourceNotFoundIs404() {
        ResponseEntity<Map<String, Object>> response =
                handler.handleResourceNotFound(new ResourceNotFoundException("Session not found"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("Spring's own typed MVC exceptions keep the status they already carry, message intact")
    void springTypedExceptionKeepsItsOwnStatus() throws Exception {
        MissingServletRequestParameterException ex =
                new MissingServletRequestParameterException("learnershipId", "Long");

        ResponseEntity<Map<String, Object>> response = handler.handleGenericException(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().get("message").toString()).contains("learnershipId");
    }

    @Test
    @DisplayName("A truly unanticipated exception never echoes its own message to the caller")
    void unanticipatedExceptionGetsOnlyTheGenericMessage() {
        // The exact bug this policy closes: whatever this message says -- a driver detail, an
        // internal path, a signed URL that leaked into some future throw site -- the caller
        // must never see it, because this exception type was never audited for that.
        NullPointerException surprise = new NullPointerException("secret/internal/detail leaked here");

        ResponseEntity<Map<String, Object>> response = handler.handleGenericException(surprise);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody().get("message")).isEqualTo("An unexpected error occurred.");
        assertThat(response.getBody().get("message").toString()).doesNotContain("secret");
    }
}
