package com.example.learnerassignments.security;

import com.example.learnerassignments.model.Learner;
import com.example.learnerassignments.repository.LearnerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Reads the authenticated learner out of the security context.
 *
 * Every learner-facing endpoint resolves identity through here. Nothing in the request —
 * path, query string or body — is allowed to name whose data is returned.
 */
@Component
@RequiredArgsConstructor
public class CurrentLearner {

    private final LearnerRepository learnerRepository;

    /** The principal, or empty when the caller is not an authenticated learner. */
    public Optional<LearnerPrincipal> principal() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || !(auth.getPrincipal() instanceof LearnerPrincipal principal)) {
            return Optional.empty();
        }
        return Optional.of(principal);
    }

    /** The principal, or a 401 if there isn't one. */
    public LearnerPrincipal require() {
        return principal().orElseThrow(NotAuthenticatedException::new);
    }

    /** The full learner record for the authenticated principal. */
    public Learner requireLearner() {
        LearnerPrincipal principal = require();
        return learnerRepository.findById(principal.learnerId())
                .orElseThrow(NotAuthenticatedException::new);
    }

    /** Raised when a learner endpoint is reached without a usable learner session. */
    public static class NotAuthenticatedException extends RuntimeException {
        public NotAuthenticatedException() {
            super("Authentication required");
        }
    }
}
