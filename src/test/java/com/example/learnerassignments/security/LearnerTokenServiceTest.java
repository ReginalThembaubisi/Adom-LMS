package com.example.learnerassignments.security;

import com.example.learnerassignments.model.Learner;
import com.example.learnerassignments.model.LearnerSession;
import com.example.learnerassignments.repository.LearnerRepository;
import com.example.learnerassignments.repository.LearnerSessionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashSet;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Revocation, checked inside a single transaction.
 *
 * That is the part worth pinning. Across requests every revoke looks correct, because each
 * request gets a fresh persistence context and reads the database as it now stands. The
 * failure mode only appears when a revoke and a subsequent check share one context — which
 * a bulk JPQL update walks straight past, and which is the natural thing for a caller to do.
 * These are @Transactional deliberately, so revoke and resolve share a context the way a
 * caller that acts on its own revoke would.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class LearnerTokenServiceTest {

    @Autowired LearnerTokenService tokenService;
    @Autowired LearnerSessionRepository sessionRepository;
    @Autowired LearnerRepository learnerRepository;
    @Autowired PasswordEncoder passwordEncoder;

    private Learner learner;

    @BeforeEach
    void setUp() {
        learner = learnerRepository.save(Learner.builder()
                .learnerCode("202600100")
                .fullName("Token Fixture")
                .passwordHash(passwordEncoder.encode("x"))
                .modules(new HashSet<>())
                .createdAt(LocalDateTime.now())
                .build());
    }

    @Test
    @DisplayName("a freshly issued token resolves to its learner")
    void issuedTokenResolves() {
        String token = tokenService.issue(learner).token();

        assertThat(tokenService.resolve(token))
                .map(LearnerPrincipal::learnerId)
                .contains(learner.getId());
    }

    @Test
    @DisplayName("the raw token is never stored, only its digest")
    void rawTokenIsNotPersisted() {
        String token = tokenService.issue(learner).token();

        assertThat(sessionRepository.findAll())
                .extracting(LearnerSession::getTokenHash)
                .noneMatch(stored -> stored.equals(token));
        assertThat(sessionRepository.findByTokenHash(LearnerTokenService.hash(token))).isPresent();
    }

    /**
     * The logout shape: revoke, then check session state in the same transaction. Passes
     * today because revoke() mutates a managed entity rather than issuing a bulk update.
     * That is a property worth holding onto — rewriting it as bulk JPQL for tidiness would
     * silently reintroduce the fault this test exists to catch.
     */
    @Test
    @DisplayName("a revoked session is dead to a check in the same transaction")
    void revokeIsVisibleWithoutLeavingTheTransaction() {
        String token = tokenService.issue(learner).token();
        assertThat(tokenService.resolve(token)).isPresent();

        tokenService.revoke(token);

        assertThat(tokenService.resolve(token))
                .as("a caller that revokes and then checks must not be told the session is live")
                .isEmpty();
    }

    /** The password-reset shape, and the regression guard for the bulk-update fix. */
    @Test
    @DisplayName("revoking every session is visible to a check in the same transaction")
    void revokeAllIsVisibleWithoutLeavingTheTransaction() {
        String first = tokenService.issue(learner).token();
        String second = tokenService.issue(learner).token();
        assertThat(tokenService.resolve(first)).isPresent();
        assertThat(tokenService.resolve(second)).isPresent();

        tokenService.revokeAllFor(learner.getId());

        assertThat(tokenService.resolve(first)).isEmpty();
        assertThat(tokenService.resolve(second)).isEmpty();
    }

    @Test
    @DisplayName("logging out of one device leaves the others signed in")
    void revokeAffectsOnlyItsOwnSession() {
        String phone = tokenService.issue(learner).token();
        String laptop = tokenService.issue(learner).token();

        tokenService.revoke(phone);

        assertThat(tokenService.resolve(phone)).isEmpty();
        assertThat(tokenService.resolve(laptop))
                .as("one logout must not sign the learner out everywhere")
                .isPresent();
    }

    @Test
    @DisplayName("an expired session does not resolve, revoked or not")
    void expiredSessionDoesNotResolve() {
        String token = tokenService.issue(learner).token();

        LearnerSession session = sessionRepository.findByTokenHash(LearnerTokenService.hash(token))
                .orElseThrow();
        session.setExpiresAt(LocalDateTime.now().minusMinutes(1));

        assertThat(tokenService.resolve(token)).isEmpty();
    }

    @Test
    @DisplayName("an unknown token is unauthenticated rather than an error")
    void unknownTokenResolvesToNothing() {
        assertThat(tokenService.resolve("not-a-token")).isEmpty();
        assertThat(tokenService.resolve("")).isEmpty();
        assertThat(tokenService.resolve(null)).isEmpty();
    }
}
